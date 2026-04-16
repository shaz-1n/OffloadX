import requests
import json
import sys

BASE_URL = "http://127.0.0.1:8000"

def test_health():
    print("[1] Testing /api/health/ ...")
    r = requests.get(f"{BASE_URL}/api/health/")
    print(f"    Status Code: {r.status_code}")
    print(f"    Response: {json.dumps(r.json(), indent=4)}")
    return r.status_code == 200

def test_composite():
    print("\n[2] Testing COMPOSITE compute (sum/avg of numbers) ...")
    payload = {
        "device_id": "test_device_001",
        "task_type": "COMPOSITE",
        "data": {"numbers": [10, 20, 30, 40, 50]}
    }
    r = requests.post(f"{BASE_URL}/api/compute/", json=payload)
    print(f"    Status Code: {r.status_code}")
    data = r.json()
    print(f"    Processing Time: {data.get('processing_time_ms')}ms")
    print(f"    Result: {json.dumps(data.get('result'), indent=4)}")
    return r.status_code == 200, data.get('task_id')

def test_complex():
    print("\n[3] Testing COMPLEX compute (matrix processing) ...")
    payload = {
        "device_id": "test_device_001",
        "task_type": "COMPLEX",
        "data": {"matrix": [[1, 2, 3], [4, 5, 6], [7, 8, 9]]}
    }
    r = requests.post(f"{BASE_URL}/api/compute/", json=payload)
    print(f"    Status Code: {r.status_code}")
    data = r.json()
    print(f"    Processing Time: {data.get('processing_time_ms')}ms")
    print(f"    Result: {json.dumps(data.get('result'), indent=4)}")
    return r.status_code == 200

def test_status(task_id):
    print(f"\n[4] Testing /api/status/{task_id}/ ...")
    r = requests.get(f"{BASE_URL}/api/status/{task_id}/")
    print(f"    Status Code: {r.status_code}")
    data = r.json()
    print(f"    Task Status: {data.get('status')}")
    print(f"    Processing Time: {data.get('processing_time_ms')}ms")
    return r.status_code == 200

def main():
    print(f"Testing OffloadX Computational Hub at {BASE_URL}")
    print("=" * 50)
    
    try:
        ok1 = test_health()
        ok2, task_id = test_composite()
        ok3 = test_complex()
        ok4 = test_status(task_id) if task_id else False

        print("\n" + "=" * 50)
        results = [ok1, ok2, ok3, ok4]
        passed = sum(results)
        print(f"Results: {passed}/{len(results)} tests passed")
        if all(results):
            print("ALL TESTS PASSED!")
        
    except requests.exceptions.ConnectionError:
        print("ERROR: Could not connect. Is the server running?")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        BASE_URL = sys.argv[1]
    main()
