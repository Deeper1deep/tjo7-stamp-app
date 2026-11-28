#!/usr/bin/env bash
# STRICT migration check. Any failed step exits non-zero (and so fails CI).
#   1. install the real OLD web build (build-26) and change its settings through its own UI
#   2. read what the old app actually persisted (its localStorage on disk) -> this is the expectation
#   3. install the new APK over it, launch it
#   4. assert the new app's saved preferences (DataStore) equal the expectation, and the UI shows it
#   5. assert the old data was not wiped by the update, and that a second update does not change anything
set -uo pipefail
PKG=com.tjo7.stamp
NEW=${1:-dist/tjo7-stamp.apk}
OLD=/tmp/old-build26.apk
SHOTS=dist/shots; mkdir -p "$SHOTS"
fail() { echo "MIGRATION: FAIL - $1"; exit 1; }
pass() { echo "MIGRATION: ok - $1"; }
shot() { adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png "$SHOTS/$1.png" >/dev/null 2>&1 || echo "MIGRATION: (screenshot $1 not saved)"; }

[ -f "$NEW" ] || fail "new apk $NEW missing"
URL=$(curl -fsSL https://api.github.com/repos/Deeper1deep/tjo7-stamp-releases/releases/tags/build-26 | python3 -c "import json,sys;d=json.load(sys.stdin);print([a['browser_download_url'] for a in d['assets'] if a['name'].endswith('.apk')][0])") || fail "cannot find the build-26 release asset"
curl -fsSL -o "$OLD" "$URL" || fail "cannot download build-26"

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
        out.append((n.get('text',''),n.get('content-desc',''),n.get('class',''),n.get('selected')=='true',list(map(int,m.groups()))))
    return out
def match(t,s,prefix): return t[0]==s or t[1]==s or (prefix and (t[0].startswith(s) or t[1].startswith(s)))
def find(s,prefix=False):
    for t in nodes():
        if match(t,s,prefix): return t
def tap(t):
    a=t[4]; subprocess.run(['adb','shell','input','tap',str((a[0]+a[2])//2),str((a[1]+a[3])//2)])
cmd,arg=sys.argv[1],sys.argv[2]; prefix=len(sys.argv)>3 and sys.argv[3]=='prefix'
tries=int(sys.argv[4]) if len(sys.argv)>4 else 10
if cmd=='tap':
    for _ in range(tries):
        t=find(arg,prefix)
        if t: tap(t); print('tapped',arg); sys.exit(0)
        time.sleep(1)
    print('NOT FOUND',arg); sys.exit(1)
if cmd=='has':
    for _ in range(tries):
        if any(arg in t[0] or arg in t[1] for t in nodes()): print('FOUND',arg); sys.exit(0)
        time.sleep(1)
    print('NOT FOUND',arg); sys.exit(1)
if cmd=='bounds':
    for _ in range(tries):
        t=find(arg,prefix)
        if t: print(*t[4]); sys.exit(0)
        time.sleep(1)
    sys.exit(1)
if cmd=='selected':
    for _ in range(tries):
        ns=nodes(); t=next((n for n in ns if match(n,arg,prefix)),None)
        if t:
            cx=(t[4][0]+t[4][2])//2; cy=(t[4][1]+t[4][3])//2
            # selected state may sit on the text node or on the clickable container around it
            sel=any(n[3] and n[4][0]<=cx<=n[4][2] and n[4][1]<=cy<=n[4][3] and (n[4][2]-n[4][0])<700 for n in ns)
            print('SELECTED' if sel else 'NOT-SELECTED', 'target', t); print('ALLNODES', [(n[0][:20],n[1][:20],n[2].split('.')[-1],n[3],n[4]) for n in ns if n[0] or n[1]][:60]); sys.exit(0 if sel else 2)
        time.sleep(1)
    print('NOT FOUND',arg); sys.exit(1)
PY

# DataStore (protobuf) -> JSON, read from the app's private dir (the CI emulator is rootable)
cat > /tmp/ds.py <<'PY'
import sys, json
def varint(b,i):
    r=0;s=0
    while True:
        c=b[i];i+=1;r|=(c&0x7f)<<s;s+=7
        if not c&0x80: return r,i
def fields(b):
    i=0
    while i<len(b):
        k,i=varint(b,i);f,w=k>>3,k&7
        if w==0: v,i=varint(b,i)
        elif w==2: l,i=varint(b,i);v=b[i:i+l];i+=l
        elif w==5: v=b[i:i+4];i+=4
        elif w==1: v=b[i:i+8];i+=8
        else: raise ValueError('wire')
        yield f,w,v
import struct
out={}
for f,w,e in fields(open(sys.argv[1],'rb').read()):
    if f!=1: continue
    key=None;val=None
    for f2,w2,v2 in fields(e):
        if f2==1: key=v2.decode()
        elif f2==2:
            for f3,w3,v3 in fields(v2):
                if f3==1: val=bool(v3)
                elif f3==2: val=round(struct.unpack('<f',v3)[0],4)
                elif f3==3: val=v3
                elif f3==5: val=v3.decode()
    out[key]=val
print(json.dumps(out))
PY
cat > /tmp/ls.py <<'PY'
import re, sys, json
b=open(sys.argv[1],'rb').read().decode('latin1')
ms=re.findall(r'\{"g":\{[^}]*\}[^\x00]*?"crop":(?:true|false)\}', b)
if not ms: print('{}'); sys.exit(0)
print(json.dumps(json.loads(ms[-1])))
PY
read_old_ls() { adb shell 'cat /data/data/com.tjo7.stamp/app_webview/Default/Local\ Storage/leveldb/*' > /tmp/ls_dump.bin 2>/dev/null; python3 /tmp/ls.py /tmp/ls_dump.bin; }

adb root >/dev/null 2>&1; sleep 3
adb uninstall $PKG >/dev/null 2>&1 || true
adb install "$OLD" >/dev/null || fail "cannot install old build-26"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 10
python3 /tmp/ui.py has "الحقوق" x 15 >/dev/null || fail "old app did not show its UI"
shot mig_old_1_editor
python3 /tmp/ui.py tap "بعدين" x 3 >/dev/null 2>&1 || true; sleep 1
# --- change settings with taps only (no keyboard)
python3 /tmp/ui.py tap "الحقوق" x 40 || fail "old app: credit tab"; sleep 2
python3 /tmp/ui.py tap "أسود" || fail "old app: black colour"; sleep 1
python3 /tmp/ui.py tap "عريض" >/dev/null 2>&1 && echo "old app: bold tapped" ; sleep 1
python3 /tmp/ui.py tap "وميض" >/dev/null 2>&1 && echo "old app: blink tapped" ; sleep 1
shot mig_old_2_credit
python3 /tmp/ui.py tap "الإخراج" x 40 || fail "old app: output tab"; sleep 2
python3 /tmp/ui.py tap "GIF" prefix || fail "old app: GIF format"; sleep 1
python3 /tmp/ui.py tap "720" prefix >/dev/null 2>&1 && echo "old app: 720 tapped"; sleep 1
python3 /tmp/ui.py tap "بدون صوت" >/dev/null 2>&1 && echo "old app: audio off tapped"; sleep 2
shot mig_old_3_output
sleep 3
OLDJSON=$(read_old_ls); echo "old app persisted: $OLDJSON"
python3 - "$OLDJSON" <<'PY' || fail "old app did not persist the changed settings (colour black + GIF expected)"
import json,sys
d=json.loads(sys.argv[1]); assert d.get('g',{}).get('color','').lower()=='#000000', 'colour'; assert d.get('fmt')=='gif','fmt'
PY
pass "old app configured and persisted settings"

adb shell am force-stop $PKG; sleep 2
adb install -r "$NEW" >/dev/null || fail "install of the new apk over the old one failed"
for try in 1 2 3; do
  adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 12
  adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | grep -q "$PKG" && break
  [ "$try" = 3 ] && fail "new app is not in the foreground after the update"
done
pass "new app launched over the old one"
python3 /tmp/ui.py has "الحقوق" x 15 >/dev/null || fail "new app UI not visible"
shot mig_new_1_editor
sleep 4

adb shell 'cat /data/data/com.tjo7.stamp/files/datastore/tjo7_settings.preferences_pb' > /tmp/ds.pb 2>/dev/null
[ -s /tmp/ds.pb ] || fail "new app has no saved preferences file after migration"
NEWJSON=$(python3 /tmp/ds.py /tmp/ds.pb) || fail "cannot decode the new app preferences"
echo "new app preferences: $NEWJSON"
python3 - "$OLDJSON" "$NEWJSON" <<'PY' || fail "migrated settings differ from what the old app had"
import json,sys
old=json.loads(sys.argv[1]); new=json.loads(sys.argv[2]); g=old.get('g',{}); bad=[]
def chk(name,exp,got):
    if exp!=got: bad.append(f'{name}: old={exp!r} new={got!r}')
chk('legacyDone',True,new.get('legacyDone'))
chk('white',g.get('color','#ffffff').lower() in ('#ffffff','#fff'),new.get('white'))
chk('bold',g.get('weight',400)==700,new.get('bold'))
chk('mode',g.get('mode','static'),new.get('mode'))
chk('corner',g.get('corner','bl'),new.get('corner'))
fmt={'webm':'mp4'}.get(old.get('fmt','auto'),old.get('fmt','auto')); chk('format',fmt,new.get('format'))
chk('size',old.get('sizeSel','orig'),new.get('size'))
chk('audio',old.get('audio',True),new.get('audio'))
chk('crop',old.get('crop',True),new.get('stickerCrop'))
chk('text',old.get('text','@t.jo7'),new.get('text'))
for k,nk in (('width','width'),('opacity','opacity'),('shadow','shadow')):
    if k in g: chk(k,int(g[k]),new.get(nk))
if 'margin' in g and abs(float(g['margin'])-float(new.get('marginF',new.get('margin',-1))))>0.01: bad.append('margin')
print('\n'.join(bad)); sys.exit(1 if bad else 0)
PY
pass "every old setting reached the new app (DataStore)"
python3 /tmp/ui.py has "GIF" x 5 >/dev/null || fail "the new app UI does not show the migrated GIF format"
pass "UI shows the migrated format"
python3 /tmp/ui.py tap "بعدين" x 3 >/dev/null 2>&1 || true; sleep 1
python3 /tmp/ui.py tap "الحقوق" || fail "new app: credit tab"; sleep 2
# The selected pill is only drawn (not exposed to accessibility), so check pixels: the chosen option's pill differs from the
# surrounding card colour, the unchosen one equals it.
adb shell screencap -p /sdcard/c.png && adb pull /sdcard/c.png /tmp/c.png >/dev/null
python3 /tmp/ui.py bounds "أسود" > /tmp/b_black.txt || fail "cannot locate the black option"
python3 /tmp/ui.py bounds "أبيض" > /tmp/b_white.txt || fail "cannot locate the white option"
python3 - <<'PY' || fail "new app does not show black as the selected colour"
from PIL import Image
im=Image.open('/tmp/c.png').convert('RGB')
def px(f):
    x1,y1,x2,y2=map(int,open(f).read().split()); return im.getpixel((max(x1-14,0),(y1+y2)//2))
b,w,bg=px('/tmp/b_black.txt'),px('/tmp/b_white.txt'),im.getpixel((6,(int(open('/tmp/b_black.txt').read().split()[1])+int(open('/tmp/b_black.txt').read().split()[3]))//2))
d=lambda a,c:sum(abs(i-j) for i,j in zip(a,c))
print('pill colours black',b,'white',w,'card',bg)
assert d(b,bg)>d(w,bg)+10, "black pill is not the highlighted one"
PY
pass "UI shows black selected"
shot mig_new_2_credit
python3 /tmp/ui.py tap "الإخراج" || fail "new app: output tab"; sleep 2
shot mig_new_3_output

# user data is preserved by the update (old localStorage still on disk) and a repeat update changes nothing
AFTER=$(read_old_ls); [ "$AFTER" = "$OLDJSON" ] || fail "old data was modified/wiped by the update"
pass "old data still on disk after the update"
adb shell am force-stop $PKG; adb install -r "$NEW" >/dev/null || fail "second install-over failed"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 10
adb shell 'cat /data/data/com.tjo7.stamp/files/datastore/tjo7_settings.preferences_pb' > /tmp/ds2.pb
[ "$(python3 /tmp/ds.py /tmp/ds2.pb)" = "$NEWJSON" ] || fail "settings changed after a second launch (migration must run only once)"
pass "migration is one-time; settings stable after re-launch"
echo "MIGRATION: ALL CHECKS PASSED"
exit 0
