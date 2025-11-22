# PowerShell script to install the BMSIT Faculty App
Write-Host "Installing BMSIT Faculty App..."
Write-Host ""

# Define ADB path
$adbPath = "C:\Users\RAKSHAK\AppData\Local\Android\Sdk\platform-tools\adb.exe"

# Check if ADB exists
if (-not (Test-Path $adbPath)) {
    Write-Host "ERROR: ADB not found at $adbPath"
    Write-Host "Please ensure Android SDK is installed correctly."
    exit 1
}

# Check connected devices
Write-Host "Checking connected devices:"
& $adbPath devices
Write-Host ""

# Get device list
$devicesOutput = & $adbPath devices
$devices = $devicesOutput | Select-String -Pattern "device$"

if ($devices.Count -eq 0) {
    Write-Host "ERROR: No Android devices found."
    Write-Host "Please connect your Android device via USB and enable USB Debugging."
    exit 1
} else {
    # Use the first device in the list
    $firstDevice = ($devices[0] -split "\s+")[0]
    Write-Host "Installing on device $firstDevice..."
    & $adbPath -s $firstDevice install -r "app\build\outputs\apk\debug\app-debug.apk"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "SUCCESS: App installed successfully on device $firstDevice!"
        Write-Host ""
        Write-Host "Launching app..."
        & $adbPath -s $firstDevice shell am start -n com.bmsit.faculty/.LoginActivity
        Write-Host "App should now be running on your device."
    } else {
        Write-Host ""
        Write-Host "ERROR: Failed to install app on device $firstDevice."
        Write-Host "Please check the error message above."
    }
}

Write-Host ""
Write-Host "Press any key to continue..."
$host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")