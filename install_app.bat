@echo off
setlocal enabledelayedexpansion
echo Installing BMSIT Faculty App...
echo.

REM Check connected devices
echo Checking connected devices:
"C:\Users\RAKSHAK\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices
echo.

REM Try to install on all connected devices
"C:\Users\RAKSHAK\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"

if %errorlevel% equ 0 (
    echo.
    echo SUCCESS: App installed successfully!
    echo.
    echo Launching app...
    "C:\Users\RAKSHAK\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.bmsit.faculty/.LoginActivity
    echo App should now be running on your device.
) else (
    echo.
    echo ERROR: Failed to install app.
    echo This might be because multiple devices are connected.
    echo Try disconnecting all but one device and run this script again.
    echo.
    echo Alternatively, you can manually install the APK:
    echo Path: app\build\outputs\apk\debug\app-debug.apk
)

echo.
pause