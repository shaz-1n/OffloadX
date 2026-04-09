from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from django.utils import timezone

from rest_framework.parsers import MultiPartParser, FormParser
from .models import ComputeTask, ComputeLog
from .serializers import ComputeTaskSerializer, ComputeRequestSerializer, FileComputeRequestSerializer
from .utils import process_compute_task, push_result_to_firebase
from .cloud_simulator import simulate_cloud_composite, simulate_cloud_complex, simulate_cloud_image


class ComputeView(APIView):
    """
    POST /api/compute/
    
    The primary endpoint. Receives data from the mobile app, processes it
    in real-time on the Hub (laptop), and returns the result immediately.
    
    This is SYNCHRONOUS for low latency -- the app sends data and gets
    the result back in the same HTTP response.
    
    Optionally pushes result to Firebase for persistent storage.
    """
    def post(self, request, format=None):
        serializer = ComputeRequestSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        device_id = serializer.validated_data['device_id']
        task_type = serializer.validated_data['task_type']
        data = serializer.validated_data['data']

        # Create a lightweight task record for tracking
        task = ComputeTask.objects.create(
            device_id=device_id,
            task_type=task_type,
            status='PROCESSING',
            processing_started_at=timezone.now(),
        )
        ComputeLog.objects.create(task=task, level='INFO', message=f"Received {task_type} task from device {device_id}")

        try:
            # --- REAL-TIME COMPUTATION ---
            compute_result = process_compute_task(data, task_type)

            # Update task with timing
            task.status = 'COMPLETED'
            task.completed_at = timezone.now()
            task.processing_time_ms = compute_result['processing_time_ms']
            task.save()

            ComputeLog.objects.create(
                task=task, level='INFO',
                message=f"Completed in {compute_result['processing_time_ms']}ms"
            )

            # Push to Firebase (non-blocking; if it fails, result is still returned)
            firebase_path = push_result_to_firebase(
                task_id=str(task.id),
                device_id=device_id,
                result=compute_result['result'],
            )
            if firebase_path:
                task.firebase_result_path = firebase_path
                task.save()

            return Response({
                'task_id': str(task.id),
                'status': 'COMPLETED',
                'processing_time_ms': compute_result['processing_time_ms'],
                'result': compute_result['result'],
                'firebase_path': firebase_path,
            }, status=status.HTTP_200_OK)

        except Exception as e:
            task.status = 'FAILED'
            task.completed_at = timezone.now()
            task.error_message = str(e)
            task.save()

            ComputeLog.objects.create(task=task, level='ERROR', message=str(e))

            return Response({
                'task_id': str(task.id),
                'status': 'FAILED',
                'error': str(e),
            }, status=status.HTTP_500_INTERNAL_SERVER_ERROR)



# =============================================================================
# Cloud Execution Endpoints  (Apr 10 milestone)
# =============================================================================

class CloudComputeView(APIView):
    """
    POST /api/cloud/compute/

    Simulates cloud-tier processing for JSON payloads (COMPOSITE / COMPLEX).
    Identical computation to /api/compute/ but includes realistic cloud
    latency overhead (network round-trip + cold-start jitter).

    The Android app calls this when ExecutionRoute == CLOUD and the user
    has no local Hub available, or when they explicitly request cloud backup.
    """
    def post(self, request, format=None):
        serializer = ComputeRequestSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        device_id  = serializer.validated_data['device_id']
        task_type  = serializer.validated_data['task_type']
        data       = serializer.validated_data['data']

        task = ComputeTask.objects.create(
            device_id=device_id,
            task_type=task_type,
            status='PROCESSING',
            processing_started_at=timezone.now(),
        )
        ComputeLog.objects.create(
            task=task, level='INFO',
            message=f"[CLOUD] Received {task_type} task from {device_id}"
        )

        try:
            import time
            t0 = time.perf_counter()

            if task_type == 'COMPOSITE':
                result = simulate_cloud_composite(data)
            else:
                result = simulate_cloud_complex(data)

            elapsed_ms = round((time.perf_counter() - t0) * 1000, 3)

            task.status = 'COMPLETED'
            task.completed_at = timezone.now()
            task.processing_time_ms = elapsed_ms
            task.save()

            ComputeLog.objects.create(
                task=task, level='INFO',
                message=f"[CLOUD] Completed in {elapsed_ms}ms (includes simulated network overhead)"
            )

            return Response({
                'task_id': str(task.id),
                'status': 'COMPLETED',
                'processing_time_ms': elapsed_ms,
                'result': result,
                'execution_tier': 'CLOUD',
            }, status=status.HTTP_200_OK)

        except Exception as e:
            task.status = 'FAILED'
            task.completed_at = timezone.now()
            task.error_message = str(e)
            task.save()
            ComputeLog.objects.create(task=task, level='ERROR', message=str(e))
            return Response({
                'task_id': str(task.id),
                'status': 'FAILED',
                'error': str(e),
                'execution_tier': 'CLOUD',
            }, status=status.HTTP_500_INTERNAL_SERVER_ERROR)


class CloudFileComputeView(APIView):
    """
    POST /api/cloud/upload/

    Simulates cloud file processing (image / video) with extra latency.
    """
    parser_classes = (MultiPartParser, FormParser)

    def post(self, request, format=None):
        serializer = FileComputeRequestSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        device_id  = serializer.validated_data['device_id']
        task_type  = serializer.validated_data['task_type']
        file_obj   = serializer.validated_data['file']
        image_mode = serializer.validated_data.get('image_mode', 'GRAYSCALE')
        pdf_mode   = serializer.validated_data.get('pdf_mode',   'ANALYZE')
        text_mode  = serializer.validated_data.get('text_mode',  'WORD_COUNT')
        video_mode = serializer.validated_data.get('video_mode', 'FACE_DETECTION')

        print(f"[CLOUD]  [CLOUD TIER] Incoming file offload from {device_id} | {round(file_obj.size / 1048576, 2)} MB")

        task = ComputeTask.objects.create(
            device_id=device_id,
            task_type=task_type,
            status='PROCESSING',
            processing_started_at=timezone.now(),
        )

        try:
            import time
            t0 = time.perf_counter()
            result = simulate_cloud_image(
                file_obj, image_mode=image_mode,
                pdf_mode=pdf_mode, text_mode=text_mode, video_mode=video_mode
            )
            elapsed_ms = round((time.perf_counter() - t0) * 1000, 3)

            task.status = 'COMPLETED'
            task.completed_at = timezone.now()
            task.processing_time_ms = elapsed_ms
            task.save()

            print(f"[CLOUD]  [CLOUD TIER] Done in {elapsed_ms}ms")

            return Response({
                'task_id': str(task.id),
                'status': 'COMPLETED',
                'processing_time_ms': elapsed_ms,
                'result': result,
                'execution_tier': 'CLOUD',
            }, status=status.HTTP_200_OK)

        except Exception as e:
            task.status = 'FAILED'
            task.error_message = str(e)
            task.save()
            return Response({'error': str(e), 'execution_tier': 'CLOUD'},
                            status=status.HTTP_500_INTERNAL_SERVER_ERROR)


class PerformanceReportView(APIView):
    """
    GET /api/report/

    Returns aggregated performance statistics across all execution tiers
    (LOCAL implied by device, HUB and CLOUD tracked server-side).
    Used by the Android Stats screen to populate the comparison chart.
    """
    def get(self, request, format=None):
        from django.db.models import Avg, Min, Max, Count

        hub_stats = (
            ComputeTask.objects
            .filter(status='COMPLETED')
            .exclude(processing_time_ms__isnull=True)
        )

        # Separate cloud vs hub by checking task source path (cloud tasks have cloud flag)
        # We tag cloud tasks by checking if they were handled by CloudComputeView
        # For now we distinguish by a naming convention: task records with >= 80ms overhead
        # have cloud overhead baked in. Instead, report both pools:
        hub_qs  = hub_stats.filter(task_type__in=['COMPOSITE', 'COMPLEX'])
        # Cloud tasks are those processed through the /cloud/ endpoints — same DB table
        # We can differentiate via ComputeLog messages containing '[CLOUD]'
        cloud_task_ids = (
            ComputeLog.objects
            .filter(message__startswith='[CLOUD]')
            .values_list('task_id', flat=True)
            .distinct()
        )

        cloud_qs = hub_stats.filter(id__in=cloud_task_ids)
        pure_hub_qs = hub_stats.exclude(id__in=cloud_task_ids)

        def _agg(qs):
            r = qs.aggregate(
                avg=Avg('processing_time_ms'),
                mn=Min('processing_time_ms'),
                mx=Max('processing_time_ms'),
                cnt=Count('id'),
            )
            return {
                'avg_ms': round(r['avg'] or 0, 2),
                'min_ms': round(r['mn'] or 0, 2),
                'max_ms': round(r['mx'] or 0, 2),
                'task_count': r['cnt'],
            }

        return Response({
            'hub': _agg(pure_hub_qs),
            'cloud': _agg(cloud_qs),
            'note': 'LOCAL times are measured on-device and stored in the Android SQLite DB.',
        })


class FileComputeView(APIView):
    """
    POST /api/upload/
    
    Handles Multipart File Uploads (e.g., Images, CSVs).
    """
    parser_classes = (MultiPartParser, FormParser)

    def post(self, request, format=None):
        serializer = FileComputeRequestSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        device_id  = serializer.validated_data['device_id']
        task_type  = serializer.validated_data['task_type']
        file_obj   = serializer.validated_data['file']
        image_mode = serializer.validated_data.get('image_mode', 'GRAYSCALE')
        pdf_mode   = serializer.validated_data.get('pdf_mode',   'ANALYZE')
        text_mode  = serializer.validated_data.get('text_mode',  'WORD_COUNT')
        video_mode = serializer.validated_data.get('video_mode', 'FACE_DETECTION')

        print("\n" + "="*70)
        print(f"[HUB] [OFFLOAD-X EDGE NODE] INCOMING OFFLOAD OVER WIFI DETECTED!")
        print("="*70)
        print(f" Source Device : {device_id}")
        print(f"[PKG] Payload Size  : {round(file_obj.size / 1048576, 2)} MB")
        print(f"[FILE] File Name     : {file_obj.name}")
        print(f"[TASK] Task Type     : {task_type} (Dynamic Heavy Compute)")
        print(f"[MODE] img={image_mode} | pdf={pdf_mode} | txt={text_mode} | vid={video_mode}")
        print("-" * 70)

        # Create Task Record
        task = ComputeTask.objects.create(
            device_id=device_id,
            task_type=task_type,
            status='PROCESSING',
            processing_started_at=timezone.now(),
        )
        task.save()

        try:
            # CALL THE LOGIC (Ported from yours)
            # We pass the file_obj directly as 'data'
            # Note: process_compute_task returns a wrapper with timing; for files
            # the result dict is nested inside 'result'. Unwrap it:
            compute_result = process_compute_task(
                file_obj, task_type,
                image_mode=image_mode,
                pdf_mode=pdf_mode,
                text_mode=text_mode,
                video_mode=video_mode,
            )

            task.status = 'COMPLETED'
            task.completed_at = timezone.now()
            task.processing_time_ms = compute_result.get('processing_time_ms', 0)
            task.save()
            
            print(f"[OK] [SUCCESS] Edge Node Computation finished in {task.processing_time_ms} ms")
            print(f" Transmitting Computed Result back to Android Device...")
            print("="*70 + "\n")

            return Response({
                'task_id': str(task.id),
                'status': 'COMPLETED',
                'processing_time_ms': task.processing_time_ms,
                'result': compute_result.get('result', compute_result), # Clean up hierarchy
            }, status=status.HTTP_200_OK)

        except Exception as e:
            task.status = 'FAILED'
            task.error_message = str(e)
            task.save()
            
            print(f"[ERR] [FAILED] Computation Error: {str(e)}")
            print("="*70 + "\n")
            
            return Response({'error': str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)


class TaskStatusView(APIView):
    """
    GET /api/status/<task_id>/
    
    Returns the status and timing metrics of a previously submitted task.
    Useful for performance comparison between local and hub execution.
    """
    def get(self, request, task_id, format=None):
        try:
            task = ComputeTask.objects.get(id=task_id)
            serializer = ComputeTaskSerializer(task)
            return Response(serializer.data)
        except ComputeTask.DoesNotExist:
            return Response({'error': 'Task not found'}, status=status.HTTP_404_NOT_FOUND)


class HubHealthView(APIView):
    """
    GET /api/health/
    
    Returns the health status of the Hub. The mobile app can use this
    to check if the Hub is reachable and decide whether to offload
    computation or process locally.
    """
    def get(self, request, format=None):
        import firebase_admin
        firebase_status = 'connected' if firebase_admin._apps else 'not_configured'

        total_tasks = ComputeTask.objects.count()
        completed = ComputeTask.objects.filter(status='COMPLETED')
        avg_time = None
        if completed.exists():
            times = [t.processing_time_ms for t in completed if t.processing_time_ms]
            avg_time = round(sum(times) / len(times), 3) if times else None

        return Response({
            'status': 'online',
            'role': 'computational_node',
            'firebase': firebase_status,
            'stats': {
                'total_tasks_processed': total_tasks,
                'avg_processing_time_ms': avg_time,
            }
        })


class SystemInfoView(APIView):
    """
    GET /api/system-info/
    
    Returns detailed hardware information about the Edge Node (laptop)
    including disk space, CPU, GPU, and OS. The Android Stats dashboard
    uses this to show real-time node health.
    """
    def get(self, request, format=None):
        import platform
        import shutil
        import os

        # Disk space
        try:
            total, used, free = shutil.disk_usage("/")
            free_gb = round(free / (1024 ** 3), 1)
            total_gb = round(total / (1024 ** 3), 1)
            free_storage = f"{free_gb} GB"
        except Exception:
            free_storage = "Unknown"
            free_gb = 0
            total_gb = 0

        # Processor
        processor = platform.processor() or platform.machine() or "Unknown"
        if len(processor) > 20:
            processor = processor[:20] + "..."

        # OS
        os_info = f"{platform.system()} {platform.release()}"

        # GPU detection
        gpu = "None"
        try:
            import subprocess
            result = subprocess.run(
                ['nvidia-smi', '--query-gpu=name', '--format=csv,noheader,nounits'],
                capture_output=True, text=True, timeout=3
            )
            if result.returncode == 0 and result.stdout.strip():
                gpu = result.stdout.strip().split('\n')[0]
        except Exception:
            pass

        # CPU count
        cpu_count = os.cpu_count() or 0

        return Response({
            'status': 'online',
            'free_storage': free_storage,
            'total_storage': f"{total_gb} GB",
            'processor': processor,
            'cpu_cores': cpu_count,
            'gpu': gpu,
            'os': os_info,
            'python_version': platform.python_version(),
        })
