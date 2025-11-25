#!/bin/bash

echo "=== IPC Two-Way Communication Test Script ==="
echo ""

echo "Step 1: Uninstalling old versions..."
adb uninstall cam.manoash.twoipcapps.appa 2>/dev/null
adb uninstall cam.manoash.twoipcapps.appb 2>/dev/null

echo "Step 2: Installing fresh APKs..."
adb install -r ipc_appA/build/outputs/apk/debug/ipc_appA-debug.apk
adb install -r ipc_appB/build/outputs/apk/debug/ipc_appB-debug.apk

echo ""
echo "Step 3: Clearing logcat..."
adb logcat -c

echo "Step 4: Starting App A..."
adb shell am start -n cam.manoash.twoipcapps.appa/.MainActivityA
sleep 2

echo "Step 5: Starting App B..."
adb shell am start -n cam.manoash.twoipcapps.appb/.MainActivityB
sleep 3

echo ""
echo "=== Checking Service Binding Status ==="
adb logcat -d | grep -E "Attempting to bind|Failed to bind|onServiceConnected" | tail -10

echo ""
echo "=== Now manually test both apps and press any key to see logs ==="
read -p "Press Enter after testing..."

echo ""
echo "=== Full Event Log ==="
adb logcat -d | grep -E "EventServiceApp|MainActivityApp" | grep -E "Sending|sendEvent|onEvent|Bound|Connected|ERROR"

echo ""
echo "=== Summary ==="
echo "App A logs:"
adb logcat -d | grep "EventServiceAppA\|MainActivityAppA" | tail -5

echo ""
echo "App B logs:"
adb logcat -d | grep "EventServiceAppB\|MainActivityAppB" | tail -5

