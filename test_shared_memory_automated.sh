#!/usr/bin/env bash
set -euo pipefail

# Automated test harness for Shared Memory IPC apps
# - Builds both apps
# - Installs APKs on connected device/emulator
# - Launches activities
# - Sends two test messages via startservice intents (one to each service)
# - Captures logcat and asserts that messages were broadcast and received

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_A="$ROOT_DIR/sharedMemory_appA/build/outputs/apk/debug/sharedMemory_appA-debug.apk"
APK_B="$ROOT_DIR/sharedMemory_appB/build/outputs/apk/debug/sharedMemory_appB-debug.apk"
PKG_A="cam.manoash.twoipcapps.sharedmemoryappa"
PKG_B="cam.manoash.twoipcapps.sharedmemoryappb"
ACT_A="${PKG_A}/.MainActivityA"
ACT_B="${PKG_B}/.MainActivityB"
SERVICE_A="${PKG_A}/cam.manoash.twoipcapps.sharedmemoryappa.SharedMemoryServiceA"
SERVICE_B="${PKG_B}/cam.manoash.twoipcapps.sharedmemoryappb.SharedMemoryServiceB"
LOG_FILTER_PATTERN="SharedMemoryServiceA|SharedMemoryServiceB|SharedMemoryAppA|SharedMemoryAppB|SharedMemoryHelper"

echo "[test] Building both apps..."
./gradlew :sharedMemory_appA:assembleDebug :sharedMemory_appB:assembleDebug --no-daemon --console=plain

if [ ! -f "$APK_A" ] || [ ! -f "$APK_B" ]; then
  echo "Built APKs not found. Exiting."
  exit 1
fi

echo "[test] Installing APKs..."
adb uninstall "$PKG_A" || true
adb uninstall "$PKG_B" || true
adb install -r "$APK_A"
adb install -r "$APK_B"

echo "[test] Clearing logcat..."
adb logcat -c || true

echo "[test] Starting activities..."
adb shell am start -n "$ACT_A" || true
sleep 1
adb shell am start -n "$ACT_B" || true
sleep 2

# Send test messages via startservice to each service
MSG_A="Automated test A -> B"
MSG_B="Automated test B -> A"

# Helper to single-quote a string for remote shell safely
remote_quote() {
  # replace each single-quote ' with '\'' sequence
  printf "%s" "$1" | sed "s/'/'\\''/g"
}

echo "[test] Sending test message from ServiceA: $MSG_A"
SAFE_A=$(remote_quote "$MSG_A")
adb shell "am startservice -n $SERVICE_A -a cam.manoash.twoipcapps.ACTION_SEND_TEST --es message '$SAFE_A'" || true
sleep 1

echo "[test] Sending test message from ServiceB: $MSG_B"
SAFE_B=$(remote_quote "$MSG_B")
adb shell "am startservice -n $SERVICE_B -a cam.manoash.twoipcapps.ACTION_SEND_TEST --es message '$SAFE_B'" || true

# Wait briefly for broadcasts/processing
sleep 2

OUT=$(adb logcat -d | grep -E "$LOG_FILTER_PATTERN" || true)

echo "\n=== Filtered log (recent entries) ==="
echo "$OUT"

# Basic assertions: ensure the messages appear as 'Sent event' logs
if echo "$OUT" | grep -q "$MSG_A"; then
  echo "[test] PASS: Message from A present in logs"
else
  echo "[test] FAIL: Message from A not present in logs"
  exit 2
fi

if echo "$OUT" | grep -q "$MSG_B"; then
  echo "[test] PASS: Message from B present in logs"
else
  echo "[test] FAIL: Message from B not present in logs"
  exit 2
fi

# Basic callback verification: look for 'Remote callback: data changed' lines
if echo "$OUT" | grep -q "Remote callback: data changed"; then
  echo "[test] PASS: Remote callback notifications observed"
else
  echo "[test] FAIL: Remote callback notifications NOT observed"
  exit 2
fi

echo "[test] SUCCESS: Automated shared-memory IPC test passed"
exit 0
