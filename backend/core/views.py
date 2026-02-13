from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from django.utils import timezone

from .models import ComputeTask, ComputeLog
from .serializers import ComputeTaskSerializer, ComputeRequestSerializer
from .utils import process_compute_task, push_result_to_firebase


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
