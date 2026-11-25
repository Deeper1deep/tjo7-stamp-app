# Signing and updates

* `applicationId` is `com.tjo7.stamp` (unchanged).
* The **original** keystore `signing/tjo7.keystore` (alias `tjo7`) is reused; nothing was generated or rotated. Its credentials live in `signing/tjo7.properties` (moved verbatim out of the old `scripts/apply_android.py`; the file is in the private repo only and is never printed by CI).
* `versionCode = GITHUB_RUN_NUMBER` exactly like the old pipeline, so numbers keep increasing (the last Capacitor release was build 26).
* CI verifies on every build that the APK's signer SHA-256 equals the keystore certificate, and (emulator job) that the new APK installs **over** the last published release with `adb install -r`.
* Update contract unchanged: `https://github.com/Deeper1deep/tjo7-stamp-releases/releases/latest/download/latest.json` = `{versionCode, versionName, size, notes[], apk}` (+ optional `sha256`, ignored by old apps). The native updater only accepts APK URLs under that repo, checks size, SHA-256, package name, versionCode and signer certificate before it offers "Install".
* Publishing happens only from `main` (or a manual run with *publish*), after unit tests, lint, build, signing check and emulator tests pass. Old releases (last five) stay available for rollback.
