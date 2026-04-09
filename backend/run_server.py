import socket
import os
import sys
import subprocess

def get_ip_address():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(0)
    try:
        s.connect(('10.254.254.254', 1))
        IP = s.getsockname()[0]
    except Exception:
        IP = '127.0.0.1'
    finally:
        s.close()
    return IP

def run_server():
    ip = get_ip_address()
    port = "8000"
    
    print("\n" + "="*65)
    print("  OffloadX Computational Hub  —  All Tiers Active")
    print("="*65)
    print(f"  Role        : Computational Node (NOT persistent storage)")
    print(f"  Framework   : Django + DRF")
    print(f"  Local IP    : {ip}")
    print(f"  Port        : {port}")
    print(f"  Base URL    : http://{ip}:{port}/")
    print("-"*65)
    print("  Hub Tier Endpoints:")
    print(f"    POST  http://{ip}:{port}/api/compute/")
    print(f"    POST  http://{ip}:{port}/api/upload/")
    print(f"    GET   http://{ip}:{port}/api/status/<task_id>/")
    print(f"    GET   http://{ip}:{port}/api/health/")
    print(f"    GET   http://{ip}:{port}/api/system-info/")
    print("-"*65)
    print("  Cloud Tier Endpoints (Apr 10 milestone):")
    print(f"    POST  http://{ip}:{port}/api/cloud/compute/")
    print(f"    POST  http://{ip}:{port}/api/cloud/upload/")
    print(f"    GET   http://{ip}:{port}/api/report/")
    print("-"*65)
    print("  Test Suite:")
    print(f"    python test_full_suite.py http://{ip}:{port}")
    print("="*65 + "\n")
    
    python_exe = sys.executable
    cmd = [python_exe, "manage.py", "runserver", "0.0.0.0:" + port]
    
    try:
        subprocess.run(cmd, check=True)
    except KeyboardInterrupt:
        print("\nHub stopped.")

if __name__ == "__main__":
    run_server()
