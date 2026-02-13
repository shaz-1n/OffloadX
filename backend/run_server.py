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
    
    print("\n" + "="*60)
    print("  OffloadX Computational Hub")
    print("="*60)
    print(f"  Role        : Computational Node (NOT storage)")
    print(f"  Framework   : Django + DRF")
    print(f"  Local IP    : {ip}")
    print(f"  Port        : {port}")
    print(f"  Base URL    : http://{ip}:{port}/")
    print("-"*60)
    print("  Endpoints:")
    print(f"    POST  http://{ip}:{port}/api/compute/")
    print(f"    GET   http://{ip}:{port}/api/status/<task_id>/")
    print(f"    GET   http://{ip}:{port}/api/health/")
    print("-"*60)
    print("  Architecture: Hybrid Storage")
    print("    SQLite   -> on mobile device (perf logs, local data)")
    print("    Firebase -> cloud (persistent, accessible storage)")
    print("    Hub      -> this laptop (real-time computation)")
    print("="*60 + "\n")
    
    python_exe = sys.executable
    cmd = [python_exe, "manage.py", "runserver", "0.0.0.0:" + port]
    
    try:
        subprocess.run(cmd, check=True)
    except KeyboardInterrupt:
        print("\nHub stopped.")

if __name__ == "__main__":
    run_server()
