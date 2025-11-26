#!/usr/bin/env bash
set -euo pipefail

# test_shared_memory.sh
# Lightweight test helper to install, launch and gather logs for
# sharedMemory_appA <-> sharedMemory_appB two-way IPC test.

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_A="$ROOT_DIR/sharedMemory_appA/build/outputs/apk/debug/sharedMemory_appA-debug.apk"
APK_B="$ROOT_DIR/sharedMemory_appB/build/outputs/apk/debug/sharedMemory_appB-debug.apk"

PKG_A="cam.manoash.twoipcapps.sharedmemoryappa"
PKG_B="cam.manoash.twoipcapps.sharedmemoryappb"
ACT_A="${PKG_A}/.MainActivityA"
ACT_B="${PKG_B}/.MainActivityB"

LOG_FILTER_PATTERN="SharedMemoryServiceA|SharedMemoryServiceB|SharedMemoryAppA|SharedMemoryAppB|SharedMemoryHelper"

echo "=== Shared Memory IPC Two-Way Test Script ==="

echo "Step 1: Ensure APKs exist (build if necessary)"
if [ ! -f "$APK_A" ]; then
  echo "APK not found: $APK_A"
  echo "Please build it: ./gradlew :sharedMemory_appA:assembleDebug"
  exit 1
fi
if [ ! -f "$APK_B" ]; then
  echo "APK not found: $APK_B"
  echo "Please build it: ./gradlew :sharedMemory_appB:assembleDebug"
  exit 1
fi

echo "Step 2: Uninstalling old versions (if installed)"
adb uninstall "$PKG_A" || true
adb uninstall "$PKG_B" || true

echo "Step 3: Installing fresh APKs"
adb install -r "$APK_A"
adb install -r "$APK_B"

echo "Step 4: Clearing logcat"
adb logcat -c || true

echo "Step 5: Starting App A: $ACT_A"
adb shell am start -n "$ACT_A" || true
sleep 2

echo "Step 6: Starting App B: $ACT_B"
adb shell am start -n "$ACT_B" || true
sleep 3

echo "\n=== Recent binding / service logs ==="
adb logcat -d | grep -E "$LOG_FILTER_PATTERN" | tail -n 40 || true

echo "\nNow interact with the two apps on the device/emulator: press their 'Send' buttons to exchange messages."
read -r -p "Press Enter when you've done some interactions and want to dump the logs..."

echo "\n=== Filtered Event Log ==="
adb logcat -d | grep -E "$LOG_FILTER_PATTERN" || true

echo "\n=== Compact Summary (latest 20 relevant lines) ==="
adb logcat -d | grep -E "$LOG_FILTER_PATTERN" | tail -n 20 || true

echo "\nDone. If logs are empty or you see errors like 'Failed to bind', ensure both apps are installed and running and the package names match the app manifests."

