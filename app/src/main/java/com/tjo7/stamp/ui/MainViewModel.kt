package com.tjo7.stamp.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tjo7.stamp.R
import com.tjo7.stamp.data.*
import com.tjo7.stamp.domain.*
import com.tjo7.stamp.media.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@android.annotation.SuppressLint("StaticFieldLeak") // holds the application context only
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val repo = SettingsRepository(ctx)
    private val importer = Importer(ctx)
    private val saver = MediaSaver(ctx)
    private val sharer = ShareHelper(ctx)
    val updater = UpdateRepository(ctx)
    val engine: MediaEngine = MediaEngine(ctx, StampRenderer(
        androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.poppins_regular) ?: android.graphics.Typeface.DEFAULT,
        androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.poppins_bold) ?: android.graphics.Typeface.DEFAULT_BOLD,
    ))

    private val _ui = MutableStateFlow(UiState(version = runCatching { updater.currentVersionName() }.getOrDefault("")))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var exportJob: Job? = null
    private var previewJob: Job? = null
    private var updateJob: Job? = null
    private var base: Bitmap? = null
    private var baseId = -1L
    private var baseSeek = -1L
    private var sample: Bitmap? = null
    private var checkedOnce = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            importer.clearWork(); engine.clearOutputs(); sharer.clear()
        }
        viewModelScope.launch {
            // One-time: carry over the old web build's saved settings (same package, so its localStorage survives the update).
            if (!repo.legacyDone()) runCatching { repo.importLegacy(LegacyImport.read(ctx)) }
            val s = repo.load()
            _ui.update { it.copy(global = s, settingsLoaded = true) }
            repo.darkTheme.collect { d -> _ui.update { it.copy(dark = d) } }
        }
        refreshPreview()
    }

    // ------------------------------------------------------------ navigation / messages
    fun setTab(t: Tab) = _ui.update { it.copy(tab = t) }
    fun consumeMessage() = _ui.update { it.copy(message = null) }
    private fun say(m: String) = _ui.update { it.copy(message = m) }
    private fun str(id: Int) = ctx.getString(id)

    // ------------------------------------------------------------ settings edits
    /** Applies [f] to the settings being edited: the file's own look when it has one, else the global settings. */
    fun edit(save: Boolean = true, f: (StampSettings) -> StampSettings) {
        _ui.update { st ->
            val it0 = st.currentItem
            if (it0?.override != null) {
                val ov = f(it0.override).sanitized()
                val g2 = f(st.global).sanitized().let { g -> st.global.copy(text = g.text, format = g.format, size = g.size, keepAudio = g.keepAudio, sticker = g.sticker, stickerCrop = g.stickerCrop, transparent = g.transparent) }
                st.copy(global = g2, items = st.items.toMutableList().also { l -> l[st.current] = it0.copy(override = ov) })
            } else st.copy(global = f(st.global).sanitized())
        }
        if (save) persist()
        refreshPreview()
    }

    fun applyPreset(p: Preset) = edit { it.applyPreset(p) }

    fun toggleOverride() {
        _ui.update { st ->
            val cur = st.currentItem ?: return@update st
            val l = st.items.toMutableList()
            l[st.current] = cur.copy(override = if (cur.override == null) st.global else null)
            st.copy(items = l)
        }
        refreshPreview()
    }

    fun setMove(on: Boolean) = _ui.update { it.copy(moveOn = on) }
    fun nudge(dx: Float, dy: Float) = edit(save = false) { it.copy(dx = it.dx + dx, dy = it.dy + dy) }
    fun resetMove() = edit(save = false) { it.copy(dx = 0f, dy = 0f) }
    fun setPreviewShow(v: Boolean) { _ui.update { it.copy(previewShow = v) }; refreshPreview() }

    fun setDark(v: Boolean) { viewModelScope.launch { repo.setDark(v) } }

    fun resetDefaults() {
        viewModelScope.launch {
            repo.reset()
            _ui.update { st -> st.copy(global = StampSettings(), items = st.items.map { it.copy(override = null) }) }
            refreshPreview(); say(str(R.string.msg_reset))
        }
    }

    fun resetAllItems() {
        _ui.update { st -> st.copy(items = st.items.map { it.copy(override = null) }) }
        refreshPreview(); say(str(R.string.msg_items_reset))
    }

    private fun persist() { val g = _ui.value.global; viewModelScope.launch { repo.save(g) } }

    // ------------------------------------------------------------ import
    fun importUris(uris: List<Uri>, fromShare: Boolean) {
        if (uris.isEmpty()) return
        _ui.update { it.copy(previewLoading = true) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { importer.import(uris, copy = fromShare) }
            val thumbs = withContext(Dispatchers.IO) { r.items.map { i -> runCatching { SourceLoader.previewFrame(ctx, i, 160) }.getOrNull() } }
            _ui.update { st ->
                val added = r.items.mapIndexed { n, i -> Item(i, null, thumbs[n]) }
                val items = st.items + added
                var g = st.global
                var msg: String? = null
                if (added.isNotEmpty() && items.all { it.info.ext == "webp" } && !g.sticker) { g = g.copy(sticker = true); msg = str(R.string.msg_sticker_auto) }
                if (r.failed > 0) msg = (msg?.plus(" ") ?: "") + ctx.getString(R.string.msg_import_failed, r.failed)
                st.copy(items = items, current = if (added.isNotEmpty()) st.items.size else st.current, global = g,
                    tab = Tab.EDITOR, message = msg, previewLoading = false)
            }
            refreshPreview()
        }
    }

    fun select(i: Int) { _ui.update { it.copy(current = i.coerceIn(0, maxOf(0, it.items.size - 1)), seekMs = 0L) }; refreshPreview() }
    fun seek(ms: Long) { _ui.update { it.copy(seekMs = ms.coerceAtLeast(0L)) }; refreshPreview() }
    fun closeResults() = _ui.update { it.copy(resultsSheet = false) }

    fun remove(i: Int) {
        _ui.update { st ->
            val l = st.items.toMutableList(); if (i in l.indices) l.removeAt(i)
            st.copy(items = l, current = st.current.coerceAtMost(maxOf(0, l.size - 1)))
        }
        refreshPreview()
    }

    // ------------------------------------------------------------ preview
    private fun refreshPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(40)
            val st = _ui.value
            val item = st.currentItem
            val s = st.effective(item)
            val bmp = withContext(Dispatchers.Default) {
                try {
                    val seekNow = if (item != null && item.info.kind != MediaKind.IMAGE) st.seekMs else -1L
                    if (item != null && (baseId != item.info.id || base == null || baseSeek != seekNow)) {
                        base = SourceLoader.previewFrame(ctx, item.info, 1080, seekNow); baseId = item.info.id; baseSeek = seekNow
                    }
                    if (item == null) { if (sample == null) sample = com.tjo7.stamp.media.SampleImage.make(); base = sample; baseId = -1 }
                    val b = if (s.transparent) null else base
                    val on = engine.previewRender(b, s, true)
                    val off = if (s.mode == CreditMode.BLINK) engine.previewRender(b, s, false) else null
                    on to off
                } catch (e: Exception) { null }
            }
            _ui.update { it.copy(preview = bmp?.first, previewOff = bmp?.second, isSample = item == null) }
        }
    }

    // ------------------------------------------------------------ export
    fun export(all: Boolean) {
        val st = _ui.value
        if (st.busy) return
        val idx = if (st.global.transparent) listOf(-1) else if (all) st.items.indices.toList() else listOfNotNull(st.current.takeIf { it in st.items.indices })
        if (idx.isEmpty()) { say(str(R.string.msg_add_first)); return }
        exportJob = viewModelScope.launch {
            _ui.update { it.copy(busy = true, progress = 0f, results = emptyList(), progressLabel = "") }
            val out = ArrayList<ResultEntry>()
            for ((k, i) in idx.withIndex()) {
                val item = st.items.getOrNull(i)
                val info = item?.info ?: dummyInfo()
                val s = st.effective(item)
                _ui.update { it.copy(progressLabel = ctx.getString(R.string.progress_item, k + 1, idx.size, info.name)) }
                try {
                    val o = engine.export(info, s) { f -> _ui.update { it.copy(progress = (k + f.coerceIn(0f, 1f)) / idx.size) } }
                    val saved = withContext(Dispatchers.IO) { runCatching { saver.save(o.file, o.name, o.format.mime) }.getOrNull() }
                    out.add(ResultEntry(info.id, o.name, o, info.sizeBytes, saved?.folder, if (saved == null) str(R.string.err_save) else null))
                } catch (e: CancellationException) {
                    _ui.update { it.copy(busy = false, message = str(R.string.msg_cancelled), results = out) }
                    throw e
                } catch (e: Exception) {
                    out.add(ResultEntry(info.id, info.name, null, info.sizeBytes, null, humanError(e)))
                }
                _ui.update { it.copy(results = out.toList()) }
            }
            val ok = out.count { it.output != null }
            val over = out.count { it.output?.overLimit == true }
            val cut = out.count { it.output?.cut != null }
            val parts = ArrayList<String>()
            if (ok > 0) parts.add(ctx.getString(R.string.msg_done, ok))
            if (out.size - ok > 0) parts.add(ctx.getString(R.string.msg_failed, out.size - ok))
            if (over > 0) parts.add(ctx.getString(R.string.msg_over_limit, over))
            if (cut > 0) parts.add(ctx.getString(R.string.msg_cut, cut))
            _ui.update { it.copy(busy = false, progress = 1f, message = parts.joinToString(" · "), resultsSheet = out.isNotEmpty()) }
        }
    }

    private fun dummyInfo() = SourceInfo(0, Uri.EMPTY, "t.jo7-overlay", "png", MediaKind.IMAGE, 1200, 360, 0, false, 0, true)

    fun cancelExport() { exportJob?.cancel() }

    private fun humanError(e: Exception): String = when {
        e.message == "memory" || e is OutOfMemoryError -> str(R.string.err_memory)
        e is UnsupportedMediaException -> str(R.string.err_unsupported)
        e.message?.startsWith("video") == true -> str(R.string.err_video)
        else -> (e.message ?: e.javaClass.simpleName).take(80)
    }

    fun retry(itemId: Long) {
        val st = _ui.value
        val i = st.items.indexOfFirst { it.info.id == itemId }
        if (i >= 0) { select(i); export(false) }
    }

    fun shareIntent(entries: List<ResultEntry>): Intent? {
        val files = entries.mapNotNull { e -> e.output?.let { it.file to it.format.mime } }
        return if (files.isEmpty()) null else sharer.intent(files)
    }

    /** Zips every successful result (sticker packs keep their own files) and saves it to Downloads/t.jo7. */
    fun saveZip() {
        val st = _ui.value
        val good = st.results.filter { it.output != null }
        if (good.isEmpty()) return
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val pack = st.items.firstOrNull { it.info.pack != null && st.results.any { r -> r.itemId == it.info.id } }?.info?.pack
                    val entries = ArrayList<Pair<String, File>>()
                    good.forEachIndexed { n, r -> entries.add((if (pack != null) r.output!!.name else "%02d-%s".format(n + 1, r.output!!.name)) to r.output.file) }
                    pack?.others?.forEach { entries.add(it) }
                    val zip = File(ctx.cacheDir, "out/" + (pack?.name ?: "t.jo7") + "-stamped.zip")
                    zip.outputStream().use { ZipUtil.write(entries, it) }
                    saver.save(zip, zip.name, "application/zip"); ctx.getString(R.string.msg_zip_saved)
                } catch (e: Exception) { ctx.getString(R.string.err_save) }
            }
            say(msg)
        }
    }

    // ------------------------------------------------------------ updates
    fun checkUpdate(manual: Boolean) {
        if (!manual && checkedOnce) return
        checkedOnce = true
        if (_ui.value.update is UpdateUi.Downloading) return
        _ui.update { it.copy(update = UpdateUi.Checking) }
        updateJob = viewModelScope.launch {
            try {
                val info = updater.fetchLatest()
                val skipped = if (manual) 0 else repo.skippedVersion.first()
                if (info != null && UpdateContract.isNewer(info.versionCode, updater.currentVersionCode(), skipped)) {
                    _ui.update { it.copy(update = UpdateUi.Available(info), updateSheet = true) }
                } else _ui.update { it.copy(update = if (manual) UpdateUi.UpToDate else UpdateUi.Idle) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _ui.update { it.copy(update = if (manual) UpdateUi.Failed(e.message ?: "") else UpdateUi.Idle) } }
        }
    }

    fun skipUpdate() {
        val u = _ui.value.update
        if (u is UpdateUi.Available) viewModelScope.launch { repo.skipVersion(u.info.versionCode) }
        _ui.update { it.copy(updateSheet = false) }
    }

    fun openUpdateSheet(v: Boolean) = _ui.update { it.copy(updateSheet = v) }

    fun downloadUpdate() {
        val info = when (val u = _ui.value.update) { is UpdateUi.Available -> u.info; is UpdateUi.Failed -> return checkUpdate(true); else -> return }
        _ui.update { it.copy(update = UpdateUi.Downloading(info, 0, info.size), updateSheet = true) }
        updateJob = viewModelScope.launch {
            try {
                updater.download(info) { d, t -> _ui.update { st -> if (st.update is UpdateUi.Downloading) st.copy(update = UpdateUi.Downloading(info, d, t)) else st } }
                _ui.update { it.copy(update = UpdateUi.Ready(info, !updater.canInstall())) }
            } catch (e: CancellationException) { _ui.update { it.copy(update = UpdateUi.Available(info)) }; throw e }
            catch (e: Exception) { _ui.update { it.copy(update = UpdateUi.Failed(e.message ?: "")) } }
        }
    }

    fun cancelDownload() { updateJob?.cancel() }

    /** Returns the intent to launch: the permission screen first when needed, otherwise the installer. */
    fun installIntent(): Intent? {
        val u = _ui.value.update as? UpdateUi.Ready ?: return null
        if (!updater.canInstall()) { _ui.update { it.copy(update = u.copy(needsPermission = true)) }; return updater.permissionIntent() }
        _ui.update { it.copy(update = u.copy(needsPermission = false)) }
        return updater.installIntent(File(ctx.cacheDir, "updates/tjo7-update.apk"))
    }

    fun autoCheck() = checkUpdate(false)
}
