@echo off
REM Change to the directory of this script
cd /d "%~dp0"

echo ========================================================
echo Setting up OffloadX Local Cloud Server...
echo ========================================================

REM Check if venv exists
if not exist "venv" (
    echo Creating virtual environment...
    python -m venv venv
)

REM Activate venv
call venv\Scripts\activate.bat

echo Installing dependencies...
pip install -r requirements.txt

echo Applying migrations...
python manage.py makemigrations core
python manage.py migrate

echo ========================================================
echo Starting Server...
echo ========================================================
python run_server.py

pause
