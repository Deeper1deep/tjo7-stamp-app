# Kotlin Native Migration (Android)

## Non-negotiable compatibility contract

- Keep the application ID exactly `com.tjo7.stamp`. Android only permits an in-place update when the installed app ID and signing certificate remain compatible.
- Keep using the existing keystore at `signing/tjo7.keystore`; do not generate, replace, or rotate it.
- Existing signing configuration in the current build pipeline is:
  - keystore file: `signing/tjo7.keystore`
  - store password: `tjo7stamp`
  - key alias: `tjo7`
  - key password: `tjo7stamp`
- Keep versionCode strictly increasing across every published APK. The current workflow uses `GITHUB_RUN_NUMBER`; the native pipeline must preserve monotonic versioning and must not reset it to 1 for a release build.
- Keep the public updater repository `Deeper1deep/tjo7-stamp-releases`, its `latest.json` contract, and the existing update-over-install behavior.
- Never publish a migration build to the update channel until its signing certificate has been verified against the currently installed app certificate and feature-parity tests pass.

## Target architecture

A fully native Android app written in Kotlin. No WebView, HTML, CSS, JavaScript, Capacitor, remote fonts, or runtime network dependency for core features. UI and resources are packaged in the APK. Image, video, GIF, and sticker processing must run locally.

Recommended layers:
- UI: Kotlin + Jetpack Compose, Material 3, Arabic RTL support.
- Domain: pure Kotlin use cases and immutable models.
- Media: Android MediaStore, Photo Picker / Storage Access Framework, Bitmap APIs, and MediaCodec for video processing where required.
- Persistence: DataStore for preferences; app-private files for temporary jobs.
- Platform integration: native share intents and Android Sharesheet.
- Updates: optional updater; update checks must never block cold start or offline use.

## Feature parity gate

Before replacing the current implementation, inventory and port every user-facing workflow from the existing app, including:
- image watermarking and its placement, size, opacity, rotation, and other existing controls;
- video watermarking and export;
- animated GIF processing and export;
- WhatsApp sticker-pack import/export and ZIP handling, where supported by the current app;
- batch processing, result preview, save-to-device, and share flows;
- settings, error states, progress, cancellation, and Arabic UI copy;
- receiving image/video/ZIP files from Android share intents.

The existing implementation is the behavioral reference. Do not silently drop a control or supported format.

## Migration stages

1. Build a native Kotlin shell with the same app ID, launcher identity, offline-first startup, and the existing signing certificate.
2. Port media-domain logic and add unit tests for image/GIF/video transformations.
3. Port screens and settings with RTL and accessibility coverage.
4. Port MediaStore save, share-intent receive, ZIP/sticker handling, and cancellation/progress.
5. Port the updater as an optional native component; validate its JSON/release contract.
6. Run feature-parity, offline airplane-mode, install-over-existing, and update-install tests.
7. Only then switch the release workflow to the native APK. Keep the current Capacitor pipeline available until cutover is proven.

## Current state

This document records the migration contract and staged plan. It is not evidence that the native rewrite or feature parity is complete. The current production branch and its APK pipeline must remain untouched during migration.
