import time
import io
import json
import os
from datetime import datetime
from django.utils import timezone

# =============================================================================
# OffloadX Computation Engine
# =============================================================================
# This is the CORE of the Computational Node. The Hub receives data from the
# mobile app, processes it here in real-time, and returns the result.
#
# The Hub does NOT store data persistently. Results are either:
#   1. Returned directly to the app in the HTTP response (low latency)
#   2. Pushed to Firebase for persistent cloud storage
# =============================================================================


def process_compute_task(data: dict, task_type: str) -> dict:
    """
    The main real-time processing function.
    
    Args:
        data: The raw payload sent from the mobile device.
        task_type: 'COMPOSITE' or 'COMPLEX'
    
    Returns:
        dict with 'result' (the computed output) and 'processing_time_ms'.
    """
    start_time = time.perf_counter()

    if task_type == 'COMPOSITE':
        result = _composite_processing(data)
    elif task_type == 'COMPLEX':
        result = _complex_processing(data)
    else:
        raise ValueError(f"Unknown task_type: {task_type}")

    elapsed_ms = (time.perf_counter() - start_time) * 1000

    return {
        'result': result,
        'processing_time_ms': round(elapsed_ms, 3),
    }


def _composite_processing(data: dict) -> dict:
    """
    Handles COMPOSITE tasks (lighter, aggregation-style workloads).
    
    Example: summing numbers, averaging sensor data, etc.
    
    TODO: Your friend (backend developer) should replace this with real logic.
    """
    # --- DEMO IMPLEMENTATION ---
    numbers = data.get('numbers', [])
    if numbers:
        return {
            'sum': sum(numbers),
            'average': sum(numbers) / len(numbers) if numbers else 0,
            'count': len(numbers),
            'min': min(numbers),
            'max': max(numbers),
        }
    
    # If no specific data, echo back with a processed flag
    return {'processed': True, 'echo': data}


def _complex_processing(data: dict) -> dict:
    """
    Handles COMPLEX tasks (heavier, CPU-intensive workloads).
    
    Example: matrix multiplication, image analysis, ML inference, etc.
    
    TODO: Your friend (backend developer) should replace this with real logic.
    """
    # --- DEMO IMPLEMENTATION ---
    # Simulate a heavier computation
    matrix = data.get('matrix', [])
    if matrix:
        # Transpose the matrix as demo processing
        transposed = list(map(list, zip(*matrix)))
        flat = [item for row in matrix for item in row]
        return {
            'transposed': transposed,
            'determinant_approx': sum(flat),  # placeholder
            'dimensions': f"{len(matrix)}x{len(matrix[0]) if matrix else 0}",
        }
    
    # Generic heavy processing simulation
    result = 0
    iterations = data.get('iterations', 10000)
    for i in range(int(iterations)):
        result += i * 0.5
    return {'computed_value': result, 'iterations': iterations}


# =============================================================================
# Firebase Integration
# =============================================================================

def push_result_to_firebase(task_id: str, device_id: str, result: dict) -> str:
    """
    Pushes the computation result to Firebase Firestore for persistent storage.
    Returns the Firestore document path.
    
    If Firebase is not initialized, returns empty string (graceful fallback).
    """
    try:
        import firebase_admin
        from firebase_admin import firestore

        if not firebase_admin._apps:
            print("Firebase not initialized. Skipping push to Firestore.")
            return ''

        db = firestore.client()
        doc_ref = db.collection('compute_results').document(str(task_id))
        doc_ref.set({
            'task_id': str(task_id),
            'device_id': device_id,
            'result': result,
            'computed_at': firestore.SERVER_TIMESTAMP,
            'status': 'COMPLETED',
        })

        path = f"compute_results/{task_id}"
        print(f"Result pushed to Firebase: {path}")
        return path

    except Exception as e:
        print(f"Firebase push failed: {e}")
        return ''
