@echo off
setlocal
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
    echo Virtual environment not found. Run setup.bat first.
    pause
    exit /b 1
)

if not exist "data\embeddings.json" (
    echo embeddings.json not found. Run ingest.bat first.
    pause
    exit /b 1
)

call .venv\Scripts\activate.bat
python -m uvicorn app:app --host 0.0.0.0 --port 8000
pause
