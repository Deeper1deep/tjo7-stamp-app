#!/usr/bin/env bash
# Real migration check: install the OLD web build (build-26), change its settings through its own UI,
# install the new APK over it, then read the new app's screen and confirm the settings came across.
PKG=com.tjo7.stamp
NEW=${1:-dist/tjo7-stamp.apk}
OLD=/tmp/old-build26.apk
mkdir -p dist/shots
URL=$(curl -fsSL https://api.github.com/repos/Deeper1deep/tjo7-stamp-releases/releases/tags/build-26 | python3 -c "import json,sys;d=json.load(sys.stdin);print([a['browser_download_url'] for a in d['assets'] if a['name'].endswith('.apk')][0])") || { echo "MIGRATION: SKIPPED (no build-26 asset)"; exit 0; }
curl -fsSL -o "$OLD" "$URL"
cat > /tmp/ui.py <<'PY'
import subprocess, sys, re, time
import xml.etree.ElementTree as ET
def dump():
    subprocess.run(['adb','shell','uiautomator','dump','/sdcard/u.xml'],capture_output=True)
    x=subprocess.run(['adb','shell','cat','/sdcard/u.xml'],capture_output=True,text=True).stdout
    i=x.find('<?xml'); return x[i:] if i>=0 else ''
def nodes():
    try: root=ET.fromstring(dump())
    except Exception: return []
    out=[]
    for n in root.iter('node'):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.get('bounds',''))
        if not m: continue
        a=list(map(int,m.groups()))
        out.append((n.get('text',''),n.get('content-desc',''),n.get('class',''),n.get('clickable'),a))
    return out
def find(pred):
    for t in nodes():
        if pred(t): return t
def tap(t):
    a=t[4]; subprocess.run(['adb','shell','input','tap',str((a[0]+a[2])//2),str((a[1]+a[3])//2)])
cmd=sys.argv[1]
if cmd=='tap_text':
    for _ in range(12):
        t=find(lambda t: t[0]==sys.argv[2] or t[1]==sys.argv[2])
        if t: tap(t); print('tapped',sys.argv[2]); sys.exit(0)
        time.sleep(1)
    print('NOT FOUND',sys.argv[2]); sys.exit(1)
if cmd=='has_text':
    for _ in range(12):
        if find(lambda t: sys.argv[2] in t[0] or sys.argv[2] in t[1]): print('FOUND',sys.argv[2]); sys.exit(0)
        time.sleep(1)
    print('NOT FOUND',sys.argv[2]); sys.exit(1)
if cmd=='edit':
    for _ in range(12):
        t=find(lambda t: 'EditText' in t[2])
        if t: tap(t); print('edit tapped, current=',t[0]); sys.exit(0)
        time.sleep(1)
    print('NO EDITTEXT'); sys.exit(1)
if cmd=='list':
    for t in nodes():
        if t[0] or t[1]: print(repr(t[0])[:40],repr(t[1])[:40],t[2].split('.')[-1])
PY
adb uninstall $PKG >/dev/null 2>&1 || true
adb install "$OLD" || { echo "MIGRATION: FAIL (old install)"; exit 0; }
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 10
adb shell screencap -p /sdcard/old_launch.png; adb pull /sdcard/old_launch.png dist/shots/mig_old_1_launch.png >/dev/null 2>&1
echo "--- old app state"; adb shell dumpsys activity activities | grep -m3 -E "mResumedActivity|topResumedActivity" ; adb logcat -d | grep -E "FATAL|AndroidRuntime|chromium.*ERROR|Process .* died" | tail -8
echo "--- old app visible nodes"; python3 /tmp/ui.py list | head -40
python3 /tmp/ui.py tap_text "الحقوق" ; sleep 2
python3 /tmp/ui.py edit; sleep 1
adb shell input keyevent KEYCODE_MOVE_END
for i in $(seq 1 40); do adb shell input keyevent KEYCODE_DEL; done
adb shell input text '@migr8test'
sleep 1
adb shell input keyevent KEYCODE_BACK 2>/dev/null; sleep 1
adb shell screencap -p /sdcard/old_edit.png; adb pull /sdcard/old_edit.png dist/shots/mig_old_2_edited.png >/dev/null 2>&1
python3 /tmp/ui.py has_text "@migr8test" || echo "MIGRATION: could not type in the old app"
python3 /tmp/ui.py tap_text "الإخراج" ; sleep 2
python3 /tmp/ui.py tap_text "GIF" ; sleep 2
adb shell screencap -p /sdcard/old_out.png; adb pull /sdcard/old_out.png dist/shots/mig_old_3_output.png >/dev/null 2>&1
sleep 4
adb shell am force-stop $PKG; sleep 2
adb install -r "$NEW" || { echo "MIGRATION: FAIL (install over)"; exit 0; }
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 12
adb shell screencap -p /sdcard/new_launch.png; adb pull /sdcard/new_launch.png dist/shots/mig_new_1_launch.png >/dev/null 2>&1
python3 /tmp/ui.py list | head -20
# close the update sheet if the live check opened it
python3 /tmp/ui.py tap_text "بعدين" >/dev/null 2>&1 || true
sleep 1
python3 /tmp/ui.py tap_text "الحقوق" ; sleep 2
adb shell screencap -p /sdcard/new_credit.png; adb pull /sdcard/new_credit.png dist/shots/mig_new_2_credit.png >/dev/null 2>&1
if python3 /tmp/ui.py has_text "@migr8test"; then echo "MIGRATION: credit text from the old app is in the new app: OK"; else echo "MIGRATION: credit text NOT found in new app: FAIL"; fi
python3 /tmp/ui.py tap_text "الإخراج" ; sleep 2
adb shell screencap -p /sdcard/new_out.png; adb pull /sdcard/new_out.png dist/shots/mig_new_3_output.png >/dev/null 2>&1
exit 0
