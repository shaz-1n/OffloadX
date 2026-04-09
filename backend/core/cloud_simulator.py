"""
OffloadX Cloud Execution Simulator
====================================
Simulates cloud-side processing (e.g. AWS Lambda / GCP Cloud Run style).

In a real deployment, the mobile app would send data to a deployed cloud
function and receive results back.  Since we are demonstrating the
LOCAL ↔ HUB ↔ CLOUD comparison in an academic setting, this module
adds a realistic network-round-trip overhead (≈ 80–300 ms extra latency)
on top of the same computation so the Stats chart shows all three tiers.
"""

import time
import random
import math


def simulate_cloud_composite(data: dict) -> dict:
    """
    Cloud tier — COMPOSITE task.
    Identical computation to hub COMPOSITE but adds realistic cloud overhead:
    - Cold-start jitter (0–120 ms)
    - Network propagation delay (40–60 ms each way)
    """
    # Simulate network + cold-start overhead
    cloud_overhead_s = random.uniform(0.08, 0.25)
    time.sleep(cloud_overhead_s)

    numbers = data.get('numbers', [])
    if numbers:
        return {
            'sum': sum(numbers),
            'average': sum(numbers) / len(numbers),
            'count': len(numbers),
            'min': min(numbers),
            'max': max(numbers),
            'cloud_overhead_ms': round(cloud_overhead_s * 1000, 1),
            'provider': 'Cloud Function (simulated)',
        }
    return {
        'processed': True,
        'echo': data,
        'cloud_overhead_ms': round(cloud_overhead_s * 1000, 1),
        'provider': 'Cloud Function (simulated)',
    }


def simulate_cloud_complex(data: dict) -> dict:
    """
    Cloud tier — COMPLEX task.
    Matrix / heavy computation with added cloud latency.
    """
    cloud_overhead_s = random.uniform(0.12, 0.35)
    time.sleep(cloud_overhead_s)

    matrix = data.get('matrix', [])
    if matrix:
        transposed = list(map(list, zip(*matrix)))
        flat = [item for row in matrix for item in row]
        return {
            'transposed': transposed,
            'determinant_approx': sum(flat),
            'dimensions': f"{len(matrix)}x{len(matrix[0]) if matrix else 0}",
            'cloud_overhead_ms': round(cloud_overhead_s * 1000, 1),
            'provider': 'Cloud Function (simulated)',
        }

    # Generic heavy compute with overhead
    result = 0
    iterations = data.get('iterations', 10000)
    for i in range(int(iterations)):
        result += i * 0.5
    return {
        'computed_value': result,
        'iterations': iterations,
        'cloud_overhead_ms': round(cloud_overhead_s * 1000, 1),
        'provider': 'Cloud Function (simulated)',
    }


def simulate_cloud_image(file_obj, image_mode: str = 'GRAYSCALE',
                         pdf_mode: str = 'ANALYZE', text_mode: str = 'WORD_COUNT',
                         video_mode: str = 'FACE_DETECTION') -> dict:
    """
    Cloud tier — FILE task.
    Routes to the correct processing pipeline based on file type, then adds
    realistic cloud overhead. All per-filetype modes are forwarded to the
    hub-tier processing functions so cloud results are consistent.
    """
    from django.core.files.base import ContentFile
    from django.core.files.storage import default_storage
    from django.conf import settings

    cloud_overhead_s = random.uniform(0.15, 0.40)
    time.sleep(cloud_overhead_s)

    timestamp = int(time.time())
    filename = getattr(file_obj, 'name', '').lower()

    def _save_and_url(data: bytes, name: str) -> str:
        path = default_storage.save(f"processed/{name}", ContentFile(data))
        return settings.MEDIA_URL + path

    try:
        # ── VIDEO ──────────────────────────────────────────────────────────
        if any(filename.endswith(ext) for ext in ('.mp4', '.mkv', '.mov', '.avi')):
            from .utils import _process_video
            result = _process_video(file_obj, video_mode, timestamp, _save_and_url)
            result['cloud_overhead_ms'] = round(cloud_overhead_s * 1000, 1)
            result['provider'] = 'Cloud Function (simulated)'
            result['mode'] = f"Cloud {result.get('mode', video_mode)}"
            return result

        # ── PDF ────────────────────────────────────────────────────────────
        if filename.endswith('.pdf'):
            from .utils import _process_pdf
            result = _process_pdf(file_obj, pdf_mode, timestamp, _save_and_url)
            result['cloud_overhead_ms'] = round(cloud_overhead_s * 1000, 1)
            result['provider'] = 'Cloud Function (simulated)'
            result['mode'] = f"Cloud {result.get('mode', pdf_mode)}"
            return result

        # ── TEXT / CSV / LOG ───────────────────────────────────────────────
        if any(filename.endswith(ext) for ext in ('.txt', '.csv', '.log', '.md', '.json', '.xml')):
            from .utils import _process_text
            result = _process_text(file_obj, text_mode, timestamp, _save_and_url)
            result['cloud_overhead_ms'] = round(cloud_overhead_s * 1000, 1)
            result['provider'] = 'Cloud Function (simulated)'
            result['mode'] = f"Cloud {result.get('mode', text_mode)}"
            return result

        # ── IMAGE (all modes) ──────────────────────────────────────────────
        from .utils import _process_image_file
        result = _process_image_file(file_obj, image_mode,
                                     pdf_mode=pdf_mode, text_mode=text_mode, video_mode=video_mode)
        result['cloud_overhead_ms'] = round(cloud_overhead_s * 1000, 1)
        result['provider'] = 'Cloud Function (simulated)'
        # Prefix mode label with "Cloud" so the UI can distinguish tiers
        result['mode'] = f"Cloud {result.get('mode', image_mode)}"
        return result

    except Exception as e:
        return {
            'status': 'error',
            'error': str(e),
            'cloud_overhead_ms': round(cloud_overhead_s * 1000, 1),
            'provider': 'Cloud Function (simulated)',
        }
