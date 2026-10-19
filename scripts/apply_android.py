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
javas = glob.glob("resources/android/java/*.java")
if not javas:
    sys.exit("ERROR: resources/android/java/*.java missing - upload the whole project folder")
for f in javas:
    shutil.copy(f, JAVA_DST)

# --- fixed signing key + increasing versionCode, so a new APK installs OVER the old one ---
import shutil as _sh
GR = "android/app/build.gradle"
KS_SRC = "signing/tjo7.keystore"
if not os.path.isfile(KS_SRC):
    sys.exit("ERROR: signing/tjo7.keystore missing - upload the whole project folder")
_sh.copy(KS_SRC, "android/app/tjo7.keystore")
g = open(GR, encoding="utf-8").read()
if "tjo7.keystore" not in g:
    sign = """signingConfigs {
        tjo7 {
            storeFile file('tjo7.keystore')
            storePassword 'tjo7stamp'
            keyAlias 'tjo7'
            keyPassword 'tjo7stamp'
        }
    }
    buildTypes {
        debug { signingConfig signingConfigs.tjo7 }"""
    if g.count("buildTypes {") != 1:
        sys.exit("ERROR: unexpected build.gradle layout (buildTypes)")
    g = g.replace("buildTypes {", sign, 1)
    g, n = re.subn(r"versionCode\s+\d+", 'versionCode Integer.parseInt(System.getenv("GITHUB_RUN_NUMBER") ?: "1")', g, count=1)
    if n != 1:
        sys.exit("ERROR: versionCode not found in build.gradle")
    open(GR, "w", encoding="utf-8").write(g)
print("icons, native plugin and fixed signing applied")
