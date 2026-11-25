# Architecture

```
com.tjo7.stamp
├── MainActivity            single activity, Compose, share-target intents, storage permission (API < 29)
├── domain/                 pure Kotlin: StampSettings, presets, geometry (StampGeometry), blink timing, WhatsApp limits, output sizing
├── media/                  media engine
│   ├── StampRenderer       Canvas drawing of the credit (shared by preview and export)
│   ├── SourceLoader        probing, EXIF-upright decoding, streaming frame iteration (GIF / animated WebP / video)
│   ├── GifCodec            GIF decoder, GIF writer (LZW), median-cut quantiser
│   ├── AnimatedWebp        WebP container demux / mux (VP8X, ANIM, ANMF)
│   ├── MediaEngine         per-format exporters, cancellation points, WhatsApp size ladders
│   └── VideoExporter       Media3 Transformer (H.264/AAC), BitmapOverlay for the credit
├── data/                   SettingsRepository (DataStore), MediaSaver (MediaStore), ShareHelper, Importer, ZipUtil, UpdateRepository
└── ui/                     MainViewModel (MVVM, StateFlow), AppState, Theme (Material 3, Poppins), Screens, Components
```

* **MVVM**: `MainViewModel` owns a single immutable `UiState`; composables only read state and call ViewModel methods.
* **Concurrency**: Kotlin coroutines. Exports run on `Dispatchers.Default`, I/O on `Dispatchers.IO`, Media3 on the main looper (required by Transformer). Every loop calls `ensureActive()` so Cancel is immediate; Transformer is cancelled through `invokeOnCancellation`.
* **Memory**: images are down-sampled above 40 MP; video is never loaded into memory (hardware transcoding); animation frames are streamed and discarded; WebP frame sets are merged when above a 96 MB budget.
* **Offline**: no network permission use except `UpdateRepository` (reads `latest.json`, downloads the APK). No external fonts/scripts.
* **Removed**: WebView, HTML, JS, Capacitor (`@capacitor/*`, `cap add android`, the four Java plugins, `apply_android.py`, Node/npm in CI).
