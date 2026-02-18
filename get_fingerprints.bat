@echo off
echo Retrieving Debug Keystore Fingerprints...
echo.

REM Check if keytool is available
keytool -help >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: keytool is not found in your PATH.
    echo Please ensure Java JDK is installed and added to your PATH.
    echo.
    pause
    exit /b 1
)

REM Get the fingerprints
echo SHA-1 and SHA-256 Fingerprints:
echo =================================
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android | findstr "SHA1: SHA256:"

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Failed to retrieve fingerprints.
    echo Make sure the debug keystore exists at: %USERPROFILE%\.android\debug.keystore
)

echo.
echo Instructions:
echo 1. Copy the SHA-1 and SHA-256 fingerprints above
echo 2. Go to Firebase Console ^> Project Settings ^> General
echo 3. Under "Your apps", find your Android app
echo 4. Add the fingerprints to the "SHA certificate fingerprints" section
echo 5. Save the changes and download the updated google-services.json file
echo.
pause