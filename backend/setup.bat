@echo off
setlocal
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
    echo Creating Python virtual environment...
    py -m venv .venv
)

call .venv\Scripts\activate.bat
python -m pip install --upgrade pip
python -m pip install -r requirements.txt

if not exist ".env" (
    copy .env.example .env >nul
    echo Created backend\.env. Add your GEMINI_API_KEY before continuing.
)

echo.
echo Backend setup complete.
echo Next: edit .env, then run ingest.bat and run.bat.
pause
