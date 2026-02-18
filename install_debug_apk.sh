#!/bin/bash

echo "Installing BMSIT Faculty App Debug APK..."
echo

# Check if ADB is available
if ! command -v adb &> /dev/null
then
    echo "ERROR: ADB is not found in your PATH."
    echo "Please ensure Android SDK platform-tools is installed and added to your PATH."
    echo
    echo "Alternatively, you can manually install the APK:"
    echo "Path: app/build/outputs/apk/debug/app-debug.apk"
    echo
    read -p "Press enter to continue..."
    exit 1
fi

# Check if device is connected
echo "Checking for connected devices..."
if ! adb devices | grep -q "device$"
then
    echo "ERROR: No Android device found."
    echo "Please connect your Android device via USB and enable USB Debugging."
    echo
    read -p "Press enter to continue..."
    exit 1
fi

echo "Device found. Installing APK..."
echo

# Uninstall existing app (if any)
echo "Uninstalling existing app (if any)..."
adb uninstall com.bmsit.faculty > /dev/null 2>&1

# Install the new APK
echo "Installing new APK..."
adb install -r "app/build/outputs/apk/debug/app-debug.apk"

if [ $? -eq 0 ]
then
    echo
    echo "SUCCESS: APK installed successfully!"
    echo
    echo "Launching app..."
    adb shell am start -n com.bmsit.faculty/.LoginActivity
    echo "App should now be running on your device."
else
    echo
    echo "ERROR: Failed to install APK."
    echo "Please check the error message above."
fi

echo
read -p "Press enter to continue..."