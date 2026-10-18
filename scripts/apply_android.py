#!/usr/bin/env python3
"""Copy the launcher icons into the generated Android project and set the icon background colour.
Run from the project root after `npx cap add android`."""
import glob, os, re, shutil, sys

RES = "android/app/src/main/res"
SRC = "resources/android"
if not os.path.isdir(RES):
    sys.exit("android/ project not found - run `npx cap add android` first")

for d in sorted(glob.glob(os.path.join(SRC, "mipmap-*"))):
    dst = os.path.join(RES, os.path.basename(d))
    os.makedirs(dst, exist_ok=True)
    for name in ("ic_launcher", "ic_launcher_round", "ic_launcher_foreground"):
        for old in glob.glob(os.path.join(dst, name + ".*")):
            os.remove(old)  # avoid duplicate-resource errors (png vs webp)
    for f in glob.glob(os.path.join(d, "*.png")):
        shutil.copy(f, dst)

bg = os.path.join(RES, "values", "ic_launcher_background.xml")
os.makedirs(os.path.dirname(bg), exist_ok=True)
with open(bg, "w", encoding="utf-8") as fh:
    fh.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#2A47E8</color>\n</resources>\n')
JAVA_DST = "android/app/src/main/java/com/tjo7/stamp"
os.makedirs(JAVA_DST, exist_ok=True)
for f in glob.glob("resources/android/java/*.java"):
    shutil.copy(f, JAVA_DST)
print("icons and native plugin applied")
