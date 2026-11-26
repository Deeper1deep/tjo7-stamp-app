#!/usr/bin/env bash
# Runs on the CI emulator: (1) instrumented tests, (2) install-over test using the previously published APK.
set -euo pipefail
PKG=com.tjo7.stamp
NEW=dist/tjo7-stamp.apk
echo "== instrumented tests"
ok=0; for i in 1 2; do gradle :app:connectedDebugAndroidTest --no-daemon --stacktrace && ok=1 && break; sleep 30; done; [ "$ok" = 1 ] || { echo "INSTRUMENTED TESTS FAILED"; adb logcat -d | tail -150; exit 1; }

echo "== screenshots"
mkdir -p dist/shots
adb pull /sdcard/Android/data/com.tjo7.stamp/files/shots/. dist/shots/ >/dev/null 2>&1 || echo "no screenshots pulled"
ls dist/shots || true

echo "== install-over test"
OLD=/tmp/old-release.apk
OLD_URL=$(curl -fsSL https://api.github.com/repos/Deeper1deep/tjo7-stamp-releases/releases/latest | python3 -c "import json,sys;d=json.load(sys.stdin);print([a['browser_download_url'] for a in d['assets'] if a['name'].endswith('.apk')][0])") || OLD_URL=""
if [ -z "$OLD_URL" ]; then echo "No previous public release found - skipping install-over"; exit 0; fi
curl -fsSL -o "$OLD" "$OLD_URL"
adb uninstall $PKG >/dev/null 2>&1 || true
adb install "$OLD"
OLD_CODE=$(adb shell dumpsys package $PKG | grep -m1 versionCode | sed -E 's/.*versionCode=([0-9]+).*/\1/')
echo "old versionCode=$OLD_CODE"
adb install -r "$NEW"
NEW_CODE=$(adb shell dumpsys package $PKG | grep -m1 versionCode | sed -E 's/.*versionCode=([0-9]+).*/\1/')
echo "new versionCode=$NEW_CODE"
test "$NEW_CODE" -gt "$OLD_CODE"
echo "INSTALL OVER OLD APP: OK ($OLD_CODE -> $NEW_CODE)"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 8
adb shell pidof $PKG >/dev/null && echo "app launches after update: OK"
