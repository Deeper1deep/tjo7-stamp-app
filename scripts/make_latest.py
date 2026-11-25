#!/usr/bin/env python3
"""Print latest.json for the in-app updater (same contract as the web build, plus an optional sha256)."""
import hashlib, json, sys

build, size, tag, apk_path = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3], sys.argv[4]
notes = [l.strip() for l in open("release-notes.txt", encoding="utf-8") if l.strip()]
sha = hashlib.sha256(open(apk_path, "rb").read()).hexdigest()
print(json.dumps({
    "versionCode": build, "versionName": "build " + str(build), "size": size, "notes": notes, "sha256": sha,
    "apk": "https://github.com/Deeper1deep/tjo7-stamp-releases/releases/download/%s/tjo7-stamp.apk" % tag,
}, ensure_ascii=False, indent=2))
