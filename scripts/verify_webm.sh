#!/usr/bin/env bash
# Strict check of an exported transparent WebM: structure, codec, timestamps, and REAL alpha decoding (libvpx).
set -uo pipefail
F=${1:?file}; fail(){ echo "WEBM: FAIL - $1"; exit 1; }
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
rm -f /tmp/a_%03d.png; ffmpeg -v error -y -c:v libvpx -i "$F" -pix_fmt rgba -frames:v 3 /tmp/a_%03d.png || fail "libvpx decode failed"
python3 - <<'PY' || fail "decoded frames have NO real transparency (alpha missing or constant)"
import glob
from PIL import Image
ok=False
for p in sorted(glob.glob('/tmp/a_*.png')):
    a=Image.open(p).convert('RGBA').getchannel('A'); lo,hi=a.getextrema()
    print(p,'alpha range',lo,hi)
    if lo<20 and hi>235: ok=True
assert ok
PY
echo "WEBM: ALPHA VERIFIED (VP8 + real alpha decoded by libvpx)"
