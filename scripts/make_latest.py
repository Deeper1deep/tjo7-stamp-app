#!/usr/bin/env python3
"""Print latest.json for the in-app updater: make_latest.py <build_number> <apk_size_bytes> <tag>"""
import json, sys
build, size, tag = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
notes = [l.strip() for l in open("release-notes.txt", encoding="utf-8") if l.strip()]
print(json.dumps({
    "versionCode": build,
    "versionName": "build " + str(build),
    "size": size,
    "notes": notes,
    "apk": "https://github.com/Deeper1deep/tjo7-stamp-releases/releases/download/%s/tjo7-stamp.apk" % tag,
}, ensure_ascii=False, indent=2))
