#!/usr/bin/env bash
# Strict check of an exported transparent WebM: structure, codec, timestamps, and REAL alpha decoding (libvpx).
set -uo pipefail
F=${1:?file}; MAXA=${2:-200}; fail(){ echo "WEBM: FAIL - $1"; exit 1; }
[ -s "$F" ] || fail "$F missing or empty"
head -c 4 "$F" | xxd -p | grep -qi '^1a45dfa3' || fail "not an EBML/Matroska file"
ffprobe -v error -show_entries stream=codec_name,width,height,avg_frame_rate,nb_read_frames -count_frames -of json "$F" > /tmp/probe.json || fail "ffprobe cannot parse the file"
cat /tmp/probe.json
python3 - <<'PY' || fail "stream is not VP8 or has no frames"
import json;s=json.load(open('/tmp/probe.json'))['streams'][0]
assert s['codec_name']=='vp8'; assert int(s['nb_read_frames'])>=2
PY
ffprobe -v error -select_streams v:0 -show_entries packet=pts_time -of csv=p=0 "$F" > /tmp/pts.txt
python3 - <<'PY' || fail "timestamps not strictly increasing"
v=[float(x.strip(',')) for x in open('/tmp/pts.txt').read().split() if x.strip(',')]
assert len(v)>=2 and all(b>a for a,b in zip(v,v[1:])), v[:10]
PY
# libvpx decoder reads the alpha BlockAdditions -> yuva420p; the native decoder would silently drop it
rm -f /tmp/a_*.png; ffmpeg -v error -y -c:v libvpx -i "$F" -pix_fmt rgba -vf scale=iw/4:ih/4 /tmp/a_%03d.png || fail "libvpx decode failed"
MAXA=$MAXA python3 - <<'PY' || fail "decoded frames have NO real transparency (alpha missing or constant)"
import glob
from PIL import Image
import os; MAXA=int(os.environ['MAXA']); fr=sorted(glob.glob('/tmp/a_*.png')); mn=255; mx=0; partial=0; opaque_px=0
for p in fr:
    im=Image.open(p).convert('RGBA'); a=im.getchannel('A'); lo,hi=a.getextrema(); mn=min(mn,lo); mx=max(mx,hi)
    h=a.histogram(); opaque_px+=sum(h[200:]); partial+=sum(h[1:200])
print('frames',len(fr),'alpha min',mn,'max',mx,'opaque px',opaque_px,'partial px',partial)
# VP8 alpha is coded as a luma plane, so full opacity decodes near 235 (limited range) rather than 255
assert len(fr)>=2 and mn<40 and mx>MAXA and (partial+opaque_px)>0
PY
echo "WEBM: ALPHA VERIFIED (VP8 + real alpha decoded by libvpx)"
