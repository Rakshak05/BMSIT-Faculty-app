@echo off
echo ============================================================
echo BMSIT Faculty Portal - Quick Start Guide
echo ============================================================
echo.

echo Step 1: Checking MongoDB...
echo.
echo Please ensure MongoDB is running before continuing.
echo If MongoDB is not installed, download it from: https://www.mongodb.com/try/download/community
echo.
echo To start MongoDB:
echo   - Windows: Start "MongoDB" service from Services
echo   - Or run: mongod
echo.
pause

echo.
echo Step 2: Creating admin user...
echo.
cd backend
python create_admin.py
if errorlevel 1 (
    echo.
    echo ❌ Failed to create admin user. Please check:
    echo    1. MongoDB is running
    echo    2. Python dependencies are installed: pip install -r requirements.txt
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo Step 3: Starting Backend Server...
echo ============================================================
echo.
echo Backend will start on http://localhost:8000
echo API Documentation: http://localhost:8000/docs
echo.
echo Press Ctrl+C to stop the server
echo.

cd app
start "BMSIT Backend" cmd /k "uvicorn main:app --reload --port 8000"

echo.
echo ============================================================
echo Step 4: Starting Frontend...
echo ============================================================
echo.
echo Opening new window for frontend...
echo Frontend will start on http://localhost:5173
echo.

cd ..\..\frontend
start "BMSIT Frontend" cmd /k "npm run dev"

echo.
echo ============================================================
echo ✅ Setup Complete!
echo ============================================================
echo.
echo Backend: http://localhost:8000
echo Frontend: http://localhost:5173
echo.
echo Login credentials:
echo   Email: admin@bmsit.in
echo   Password: Admin@123
echo.
echo ⚠️  Remember to change the admin password after first login!
echo.
echo Press any key to open the application in your browser...
pause > nul

start http://localhost:5173/login

echo.
echo Both servers are running in separate windows.
echo Close those windows to stop the servers.
echo.
pause
