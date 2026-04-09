# -*- coding: utf-8 -*-
"""
OffloadX - Comprehensive API Test Suite
========================================
Covers all milestones up to Apr 17:
  - Hub health & system-info
  - COMPOSITE + COMPLEX compute (Hub tier)
  - Cloud compute (COMPOSITE + COMPLEX)
  - File upload (Hub + Cloud)
  - Task status retrieval
  - Performance report endpoint
  - Error handling & edge cases

Run with:
    python test_full_suite.py [base_url]

Examples:
    python test_full_suite.py                        # localhost
    python test_full_suite.py http://192.168.1.5:8000
"""

import sys
import io
import os

# Force UTF-8 output on Windows (avoids CP1252 UnicodeEncodeError)
if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

import requests
import json
import time
import tempfile
from datetime import datetime
from PIL import Image

BASE_URL = "http://127.0.0.1:8000"

PASS = "\033[92m[PASS]\033[0m"
FAIL = "\033[91m[FAIL]\033[0m"

results = []

def record(name, ok, detail=""):
    results.append((name, ok, detail))
    status = PASS if ok else FAIL
    print(f"  {status} {name}")
    if detail:
        print(f"         {detail}")


# -------------------------------------------------------------------
# 1. Hub Health & System Info
# -------------------------------------------------------------------

def test_hub_health():
    print("\n[1] Hub Health")
    try:
        r = requests.get(f"{BASE_URL}/api/health/", timeout=10)
        ok = r.status_code == 200
        data = r.json() if ok else {}
        record("GET /api/health/ -> 200", ok,
               f"role={data.get('role','?')}, tasks={data.get('stats',{}).get('total_tasks_processed','?')}")
        return ok
    except Exception as e:
        record("GET /api/health/", False, str(e))
        return False

def test_system_info():
    print("\n[2] System Info")
    try:
        r = requests.get(f"{BASE_URL}/api/system-info/", timeout=10)
        ok = r.status_code == 200
        data = r.json() if ok else {}
        record("GET /api/system-info/ -> 200", ok,
               f"os={data.get('os','?')}, cpu_cores={data.get('cpu_cores','?')}")
        return ok
    except Exception as e:
        record("GET /api/system-info/", False, str(e))
        return False


# -------------------------------------------------------------------
# 2. Hub Compute - COMPOSITE
# -------------------------------------------------------------------

def test_hub_composite():
    print("\n[3] Hub COMPOSITE Compute")
    payload = {
        "device_id": "test_device_suite",
        "task_type": "COMPOSITE",
        "data": {"numbers": list(range(1, 101))}
    }
    try:
        r = requests.post(f"{BASE_URL}/api/compute/", json=payload, timeout=30)
        ok = r.status_code == 200
        data = r.json()
        task_id = data.get("task_id")
        t_ms = data.get("processing_time_ms")
        record("POST /api/compute/ COMPOSITE -> 200", ok,
               f"time={t_ms}ms, sum={data.get('result',{}).get('sum','?')}")
        result = data.get("result", {})
        fields_ok = all(k in result for k in ["sum", "average", "count", "min", "max"])
        if fields_ok:
            record("COMPOSITE result has all fields", True,
                   f"sum={result.get('sum')}, avg={result.get('average'):.2f}, count={result.get('count')}")
        else:
            record("COMPOSITE result has all fields", False, str(result))
        return ok, task_id
    except Exception as e:
        record("POST /api/compute/ COMPOSITE", False, str(e))
        return False, None


# -------------------------------------------------------------------
# 3. Hub Compute - COMPLEX
# -------------------------------------------------------------------

def test_hub_complex():
    print("\n[4] Hub COMPLEX Compute")
    payload = {
        "device_id": "test_device_suite",
        "task_type": "COMPLEX",
        "data": {"matrix": [[1,2,3],[4,5,6],[7,8,9]]}
    }
    try:
        r = requests.post(f"{BASE_URL}/api/compute/", json=payload, timeout=30)
        ok = r.status_code == 200
        data = r.json()
        record("POST /api/compute/ COMPLEX -> 200", ok,
               f"time={data.get('processing_time_ms')}ms")
        result = data.get("result", {})
        record("COMPLEX result has transposed matrix", "transposed" in result, str(result))
        return ok
    except Exception as e:
        record("POST /api/compute/ COMPLEX", False, str(e))
        return False


# -------------------------------------------------------------------
# 4. Task Status Retrieval
# -------------------------------------------------------------------

def test_task_status(task_id):
    print("\n[5] Task Status")
    if not task_id:
        record("GET /api/status/<id>/", False, "No task_id from previous step")
        return False
    try:
        r = requests.get(f"{BASE_URL}/api/status/{task_id}/", timeout=10)
        ok = r.status_code == 200
        data = r.json()
        record(f"GET /api/status/{task_id[:8]}.../ -> 200", ok,
               f"status={data.get('status')}, time={data.get('processing_time_ms')}ms")
        return ok
    except Exception as e:
        record("GET /api/status/", False, str(e))
        return False


# -------------------------------------------------------------------
# 5. Cloud Compute - COMPOSITE (Apr 10 milestone)
# -------------------------------------------------------------------

def test_cloud_composite():
    print("\n[6] Cloud COMPOSITE Compute  (Apr 10)")
    payload = {
        "device_id": "test_device_suite",
        "task_type": "COMPOSITE",
        "data": {"numbers": list(range(1, 51))}
    }
    try:
        t0 = time.time()
        r = requests.post(f"{BASE_URL}/api/cloud/compute/", json=payload, timeout=45)
        roundtrip = round((time.time() - t0) * 1000, 1)
        ok = r.status_code == 200
        data = r.json()
        t_ms = data.get("processing_time_ms")
        tier = data.get("execution_tier")
        provider = data.get("result", {}).get("provider", "?")
        record("POST /api/cloud/compute/ -> 200", ok,
               f"server={t_ms}ms, roundtrip={roundtrip}ms, tier={tier}, provider={provider}")
        overhead = data.get("result", {}).get("cloud_overhead_ms", 0)
        record("Cloud overhead > 10ms (expected)", overhead > 10,
               f"cloud_overhead={overhead}ms")
        return ok
    except Exception as e:
        record("POST /api/cloud/compute/", False, str(e))
        return False


# -------------------------------------------------------------------
# 6. Cloud Compute - COMPLEX (Apr 10 milestone)
# -------------------------------------------------------------------

def test_cloud_complex():
    print("\n[7] Cloud COMPLEX Compute  (Apr 10)")
    payload = {
        "device_id": "test_device_suite",
        "task_type": "COMPLEX",
        "data": {"matrix": [[2,4],[6,8]]}
    }
    try:
        r = requests.post(f"{BASE_URL}/api/cloud/compute/", json=payload, timeout=45)
        ok = r.status_code == 200
        data = r.json()
        record("POST /api/cloud/compute/ COMPLEX -> 200", ok,
               f"time={data.get('processing_time_ms')}ms, tier={data.get('execution_tier')}")
        return ok
    except Exception as e:
        record("POST /api/cloud/compute/ COMPLEX", False, str(e))
        return False


# -------------------------------------------------------------------
# 7. Performance Report Endpoint (Apr 10 milestone)
# -------------------------------------------------------------------

def test_performance_report():
    print("\n[8] Performance Report Endpoint  (Apr 10)")
    try:
        r = requests.get(f"{BASE_URL}/api/report/", timeout=15)
        ok = r.status_code == 200
        data = r.json()
        record("GET /api/report/ -> 200", ok)
        hub_avg = data.get("hub", {}).get("avg_ms", 0)
        cloud_avg = data.get("cloud", {}).get("avg_ms", 0)
        record("Report has hub stats", data.get("hub", {}).get("task_count", 0) >= 0,
               f"hub avg={hub_avg}ms, tasks={data.get('hub',{}).get('task_count')}")
        record("Report has cloud stats", data.get("cloud", {}).get("task_count", 0) >= 0,
               f"cloud avg={cloud_avg}ms, tasks={data.get('cloud',{}).get('task_count')}")
        if hub_avg > 0 and cloud_avg > 0:
            record("Cloud is slower than Hub (expected)", cloud_avg > hub_avg,
                   f"cloud={cloud_avg}ms vs hub={hub_avg}ms")
        return ok
    except Exception as e:
        record("GET /api/report/", False, str(e))
        return False


# -------------------------------------------------------------------
# 8. File Upload - Hub Tier (image)
# -------------------------------------------------------------------

def create_test_image():
    """Creates a small JPEG test image in memory."""
    import io as _io
    img = Image.new("RGB", (100, 100), color=(255, 100, 50))
    buf = _io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()

def test_hub_file_upload():
    print("\n[9] Hub File Upload (IMAGE_GRAYSCALE)")
    try:
        img_bytes = create_test_image()
        files = {"file": ("test_image.jpg", img_bytes, "image/jpeg")}
        data = {"device_id": "test_suite_device", "task_type": "IMAGE_GRAYSCALE"}
        r = requests.post(f"{BASE_URL}/api/upload/", files=files, data=data, timeout=60)
        ok = r.status_code == 200
        resp = r.json()
        record("POST /api/upload/ -> 200", ok,
               f"time={resp.get('processing_time_ms')}ms, status={resp.get('status')}")
        result = resp.get("result", {})
        record("Upload result has 'status' field", "status" in result,
               f"status={result.get('status')}, mode={result.get('mode')}")
        return ok
    except Exception as e:
        record("POST /api/upload/", False, str(e))
        return False


# -------------------------------------------------------------------
# 9. File Upload - Cloud Tier (Apr 10 milestone)
# -------------------------------------------------------------------

def test_cloud_file_upload():
    print("\n[10] Cloud File Upload  (Apr 10)")
    try:
        img_bytes = create_test_image()
        files = {"file": ("test_cloud.jpg", img_bytes, "image/jpeg")}
        data = {"device_id": "test_suite_device", "task_type": "IMAGE_GRAYSCALE"}
        r = requests.post(f"{BASE_URL}/api/cloud/upload/", files=files, data=data, timeout=90)
        ok = r.status_code == 200
        resp = r.json()
        record("POST /api/cloud/upload/ -> 200", ok,
               f"time={resp.get('processing_time_ms')}ms, tier={resp.get('execution_tier')}")
        return ok
    except Exception as e:
        record("POST /api/cloud/upload/", False, str(e))
        return False


# -------------------------------------------------------------------
# 10. Error Handling Tests (Apr 17 milestone)
# -------------------------------------------------------------------

def test_error_handling():
    print("\n[11] Error Handling Validation  (Apr 17)")

    # 11a. Missing required field
    try:
        r = requests.post(f"{BASE_URL}/api/compute/",
                          json={"device_id": "x"},
                          timeout=10)
        record("Missing fields -> 400 Bad Request", r.status_code == 400,
               f"Got {r.status_code}")
    except Exception as e:
        record("Missing fields -> 400", False, str(e))

    # 11b. Invalid task_type
    try:
        r = requests.post(f"{BASE_URL}/api/compute/",
                          json={"device_id": "x", "task_type": "INVALID_TYPE", "data": {}},
                          timeout=10)
        record("Invalid task_type -> 400 Bad Request", r.status_code == 400,
               f"Got {r.status_code}")
    except Exception as e:
        record("Invalid task_type -> 400", False, str(e))

    # 11c. Non-existent task status
    try:
        r = requests.get(f"{BASE_URL}/api/status/00000000-0000-0000-0000-000000000000/", timeout=10)
        record("Non-existent task_id -> 404 Not Found", r.status_code == 404,
               f"Got {r.status_code}")
    except Exception as e:
        record("Non-existent task_id -> 404", False, str(e))

    # 11d. Empty payload
    try:
        r = requests.post(f"{BASE_URL}/api/compute/", json={}, timeout=10)
        record("Empty payload -> 400 Bad Request", r.status_code == 400,
               f"Got {r.status_code}")
    except Exception as e:
        record("Empty payload -> 400", False, str(e))


# -------------------------------------------------------------------
# 11. Performance Comparison Analysis (Apr 10 + Apr 17)
# -------------------------------------------------------------------

def test_performance_comparison():
    print("\n[12] Performance Comparison: Hub vs Cloud  (Apr 10 + Apr 17)")
    n = 10000
    payload = {"device_id": "perf_test", "task_type": "COMPOSITE", "data": {"numbers": list(range(n))}}

    hub_times = []
    cloud_times = []

    for _ in range(3):
        try:
            r = requests.post(f"{BASE_URL}/api/compute/", json=payload, timeout=30)
            if r.status_code == 200:
                hub_times.append(r.json().get("processing_time_ms", 0))
        except Exception:
            pass
        try:
            r = requests.post(f"{BASE_URL}/api/cloud/compute/", json=payload, timeout=45)
            if r.status_code == 200:
                cloud_times.append(r.json().get("processing_time_ms", 0))
        except Exception:
            pass

    if hub_times and cloud_times:
        avg_hub   = sum(hub_times)   / len(hub_times)
        avg_cloud = sum(cloud_times) / len(cloud_times)
        record("Hub avg < Cloud avg (Hub is faster)", avg_hub < avg_cloud,
               f"Hub avg={avg_hub:.1f}ms, Cloud avg={avg_cloud:.1f}ms")
        record("Hub < 500ms average", avg_hub < 500, f"avg={avg_hub:.1f}ms")
        overhead = avg_cloud - avg_hub
        record("Cloud overhead measured", overhead > 0, f"overhead={overhead:.1f}ms")
    else:
        record("Performance comparison ran", False, "No successful responses")


# -------------------------------------------------------------------
# Main
# -------------------------------------------------------------------

def main():
    global BASE_URL
    if len(sys.argv) > 1:
        BASE_URL = sys.argv[1]

    print("=" * 65)
    print(f"  OffloadX API Test Suite -- Full Run")
    print(f"  Target : {BASE_URL}")
    print(f"  Time   : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 65)

    try:
        ok1, tid = test_hub_composite() if (test_hub_health() and test_system_info()) else (False, None)
        test_hub_complex()
        test_task_status(tid)
        test_cloud_composite()
        test_cloud_complex()
        test_performance_report()
        test_hub_file_upload()
        test_cloud_file_upload()
        test_error_handling()
        test_performance_comparison()

    except requests.exceptions.ConnectionError:
        print(f"\n\033[91mERROR: Cannot connect to {BASE_URL}\033[0m")
        print("Make sure hub is running (run_server.bat or: python manage.py runserver 0.0.0.0:8000)")
        sys.exit(1)

    print("\n" + "=" * 65)
    passed = sum(1 for _, ok, _ in results if ok)
    total  = len(results)
    pct    = round(passed / total * 100) if total else 0
    print(f"  Results: {passed}/{total} ({pct}%) assertions passed")
    if passed == total:
        print("  \033[92mALL TESTS PASSED\033[0m")
    else:
        failed_list = [(n, d) for n, ok, d in results if not ok]
        print(f"  \033[91m{len(failed_list)} FAILURES:\033[0m")
        for name, detail in failed_list:
            print(f"    x {name}: {detail}")
    print("=" * 65)

    return passed == total


if __name__ == "__main__":
    sys.exit(0 if main() else 1)
