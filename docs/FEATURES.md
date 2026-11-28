# Feature inventory: old app (HTML/JS + Capacitor) → native Kotlin

The old production app (branch `main`, `www/index.html` ≈ 2,500 lines of JS + 4 Java plugins) was the behavioural reference.

| # | Old feature | Native implementation | Status / difference |
|---|-------------|----------------------|---------------------|
| 1 | Credit text (default `@t.jo7`, ≤60 chars) | `StampSettings.text`, `StampRenderer.drawCredit` (Canvas, Poppins + system fallback for Arabic) | Same |
| 2 | Corner (TL/TR/BL/BR), default bottom-left | `Corner`, `StampGeometry.place` | Same |
| 3 | Size %, margin %, opacity %, shadow % | Sliders in the Credit tab; same formulas (font size from width fraction, padding from frame width, layer alpha, shadow blur) | Same |
| 4 | White / black, regular / bold (bold adds outline) | Same | Same |
| 5 | Static or blink mode (visible in 2nd second of each 3 s) | `creditVisibleAt`; video uses a time-varying Media3 `BitmapOverlay` | Same |
| 6 | Presets "credit" / "blink" | `StampSettings.applyPreset` | Same |
| 7 | Per-file override + reset | "Per-file settings" switch in the Credit tab (`Item.override`) | Same |
| 8 | Optional move-credit (arrows + drag), session only | `RepeatButton` arrows (hold to repeat) and drag on preview; `dx/dy` never persisted | Same |
| 9 | Images (PNG/JPG/WebP) | `SourceLoader.decodeStill` (EXIF-upright, down-sampled above 40 MP) → `MediaEngine.exportStill` | EXIF metadata is not copied to the output (orientation is baked in) |
| 10 | Animated GIF in/out | Own streaming decoder + encoder (`GifCodec.kt`): median-cut palette, LZW, delta frames | Same |
| 11 | Video in/out | Media3 Transformer, H.264/AAC MP4, overlay composed on the GPU, source bitrate kept, audio kept (toggle) | **Video output is MP4, not WebM.** Old "WebM" choices map to MP4. Transparent WebM exists only for the credit-only overlay (row 16) |
| 12 | Video → GIF (max 15 s, tagged "cut") | `MediaEngine.exportGif`, 20 fps sampling, cap 15 s, result shows a "cut" tag | Same |
| 13 | Output formats auto/GIF/MP4(WebM)/PNG/JPG | `OutFormat` + chips | WebP also selectable |
| 14 | Output size orig/1080/720/540, never upscale, motion capped ~8.3 MP | `outputSize` | Same |
| 15 | Keep audio toggle | `StampSettings.keepAudio` | Same |
| 16 | Transparent overlay (PNG/GIF/WebM) | "Credit only" switch → 1200×360 transparent PNG / GIF (blink = 3 frames) / animated WebP | Transparent **WebM (VP8 + alpha)** is also offered. VERIFIED in CI by decoding an exported file with libvpx: 45 frames, alpha 0..255, strictly increasing timestamps (`scripts/verify_webm.sh`). Needs a device with a VP8 hardware/software encoder (all Android 5+ have the software one); if encoding fails the app reports it and GIF/PNG/WebP remain available. Not yet checked in external editors/players on a physical phone (see docs/DEVICE_TEST_CHECKLIST.md) |
| 17 | WhatsApp sticker mode: 512×512 WebP, crop to content, ≤100 KB static / ≤500 KB animated / ≤10 s | `exportStill` / `exportAnimWebp`, quality ladder + frame merging, own animated-WebP muxer (`AnimatedWebp.kt`) | Same; limits shown as tags |
| 18 | Sticker packs (`.zip`, `.wastickers`, `.enc`) in, pack zip out | `Importer` + `ZipUtil` (zip-slip safe, size capped), "Save ZIP" keeps pack metadata files | Same |
| 19 | Auto sticker mode when every file is `.webp` | `MainViewModel.importUris` | Same |
| 20 | Multiple files, batch export, results with before→after sizes | Editor tab: film strip, "Export all", results list | Same |
| 21 | Save to Pictures/t.jo7, Movies/t.jo7 | `MediaSaver` (MediaStore, `IS_PENDING`; legacy path + runtime permission below Android 10) | Same |
| 22 | ZIP for multiple files | "Save ZIP" → Downloads/t.jo7 | Same |
| 23 | Per-file share / share all | `ShareHelper` (FileProvider + chooser) | Same |
| 24 | Receive files from the gallery (SEND / SEND_MULTIPLE image, video, zip) | Manifest filters + `MainActivity.handleIntent`, files copied to cache | Same |
| 25 | Retry a failed file | Results list "Retry" | Same |
| 26 | Persistence of settings (`tjo7.settings.v1`) | DataStore Preferences | Old browser storage is not migrated (different storage engine); defaults are identical |
| 27 | Dark / light theme (dark default) | Material 3 dark/light, switch in Settings | Same |
| 28 | In-app updater (check, notes, download with progress, install over old, "later") | `UpdateRepository` + `UpdateSheet`; same `latest.json`; adds SHA-256 + package + signer verification | Same contract |
| 29 | Footer credit "@Deeper" → WhatsApp link | Settings → About | Same |
| 30 | 4-tab layout (Editor, Credit, Output, Settings) | Material 3 `NavigationBar`, RTL | Same |
| 31 | Offline fonts | Poppins bundled in `res/font` | No network fonts |

## Known limitations of the native version

* Live animated preview is a still frame (video: frame at ~1 s); a "hide credit" button shows the blink-off state.
* Processing runs while the app is open (screen is kept awake during export). There is no foreground service, so leaving the app for a long time may let Android suspend it.
* Whole-file memory use: GIF and animated WebP **sources** are read into memory (limit 96 MB). Videos and images are streamed/down-sampled; video export never loads the video into memory.
* Not verified on a physical phone: only on the CI emulator (API 33) and by unit tests.

## Verified in CI on every build (Android 13 emulator)
Old build-26 -> new app: all settings migrated (DataStore compared with the old localStorage), UI shows them, old data still on disk, migration runs once (`scripts/migrate_check.sh`). Install-over the published build without uninstalling. The one-time migration uses a hidden WebView only to read the old localStorage and destroys it immediately (the single documented exception to "no WebView").

## Known limitations
* No live (playing) preview: Media3 live overlay preview was not implemented, so it is NOT claimed. The preview is a still frame.
* Not run on a physical phone by us; use docs/DEVICE_TEST_CHECKLIST.md.
