@echo off
echo Installing BMSIT Faculty App Debug APK...
echo.

REM Check if ADB is available
adb version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: ADB is not found in your PATH.
    echo Please ensure Android SDK platform-tools is installed and added to your PATH.
    echo.
    echo Alternatively, you can manually install the APK:
    echo Path: app\build\outputs\apk\debug\app-debug.apk
    echo.
    pause
    exit /b 1
)

REM Check if device is connected
echo Checking for connected devices...
adb devices | findstr "device$"
if %errorlevel% neq 0 (
    echo ERROR: No Android device found.
    echo Please connect your Android device via USB and enable USB Debugging.
    echo.
    pause
    exit /b 1
)

echo Device found. Installing APK...
echo.

REM Uninstall existing app (if any)
echo Uninstalling existing app (if any)...
adb uninstall com.bmsit.faculty >nul 2>&1

REM Install the new APK
echo Installing new APK...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"

if %errorlevel% equ 0 (
    echo.
    echo SUCCESS: APK installed successfully!
    echo.
    echo Launching app...
    adb shell am start -n com.bmsit.faculty/.LoginActivity
    echo App should now be running on your device.
) else (
    echo.
    echo ERROR: Failed to install APK.
    echo Please check the error message above.
)

echo.
pause