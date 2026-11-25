package com.tjo7.stamp.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import com.tjo7.stamp.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File

class ExportOutput(
    val file: File, val name: String, val format: OutFormat,
    /** (kept seconds, full seconds) when the clip had to be shortened. */
    val cut: Pair<Double, Double>?,
    /** Size limit that applies (WhatsApp stickers), 0 when none. */
    val limit: Int,
) {
    val size get() = file.length()
    val overLimit get() = limit > 0 && size > limit
}

class ExportException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

/** Turns an imported item + settings into a finished file in the cache. All heavy work runs off the main thread. */
class MediaEngine(private val ctx: Context, val renderer: StampRenderer) {
    private val outDir = File(ctx.cacheDir, "out").apply { mkdirs() }
    private val videoExporter = VideoExporter(ctx, renderer)

    fun resolveFormat(info: SourceInfo, s: StampSettings): OutFormat {
        if (s.sticker && !s.transparent) return OutFormat.WEBP
        if (s.transparent) return when (s.format) { OutFormat.PNG -> OutFormat.PNG; OutFormat.WEBP -> OutFormat.WEBP; else -> OutFormat.GIF }
        if (s.format != OutFormat.AUTO) return s.format
        return when (info.kind) {
            MediaKind.ANIM_GIF, MediaKind.ANIM_WEBP -> OutFormat.GIF
            MediaKind.VIDEO -> OutFormat.MP4
            MediaKind.IMAGE -> when {
                s.mode == CreditMode.BLINK -> OutFormat.GIF
                info.ext == "jpg" || info.ext == "jpeg" -> OutFormat.JPG
                info.ext == "webp" -> OutFormat.WEBP
                else -> OutFormat.PNG
            }
        }
    }

    fun baseName(n: String): String =
        n.substringBeforeLast('.').replace(Regex("[^\\w\\-. \\u0600-\\u06FF]+"), "_").take(40).ifEmpty { "file" }

    private val boxCache = HashMap<Long, IntArray?>()

    private fun stickerBox(info: SourceInfo): IntArray? = synchronized(boxCache) {
        if (boxCache.containsKey(info.id)) return boxCache[info.id]
        val box: IntArray? = try {
            when (info.kind) {
                MediaKind.VIDEO -> null
                MediaKind.IMAGE -> StampRenderer.contentBox(SourceLoader.decodeStill(ctx, info, 512))
                    ?.let { scaleBox(it, info, 512) }
                else -> {
                    var u: IntArray? = null; var n = 0
                    SourceLoader.forEachFrame(ctx, info, 600.0, 1.0, 0, 1) { b, _, _ ->
                        if (n++ % 4 == 0) StampRenderer.contentBox(b)?.let { c -> u = if (u == null) c else intArrayOf(minOf(u!![0], c[0]), minOf(u!![1], c[1]), maxOf(u!![2], c[2]), maxOf(u!![3], c[3])) }
                        true
                    }
                    u
                }
            }
        } catch (_: Exception) { null }
        boxCache[info.id] = box
        box
    }

    private fun scaleBox(b: IntArray, info: SourceInfo, decodedMax: Int): IntArray {
        val k = maxOf(info.width, info.height).toDouble() / maxOf(decodedMax, 1).coerceAtLeast(1)
        val dw = minOf(info.width, decodedMax); val dh = minOf(info.height, decodedMax)
        val kk = if (maxOf(info.width, info.height) <= decodedMax) 1.0 else k
        @Suppress("UNUSED_VARIABLE") val unused = dw + dh
        return intArrayOf(
            (b[0] * kk).toInt(), (b[1] * kk).toInt(),
            minOf(info.width, Math.ceil(b[2] * kk).toInt()), minOf(info.height, Math.ceil(b[3] * kk).toInt()),
        )
    }

    private fun stickerDims(s: StampSettings, box: IntArray?): Pair<Int, Int> {
        if (s.stickerCrop && box != null) {
            val bw = maxOf(1, box[2] - box[0]); val bh = maxOf(1, box[3] - box[1])
            val k = minOf(1.0, WhatsApp.SIZE / maxOf(bw, bh).toDouble())
            return maxOf(1, Math.round(bw * k).toInt()) to maxOf(1, Math.round(bh * k).toInt())
        }
        return WhatsApp.SIZE to WhatsApp.SIZE
    }

    private fun frameDims(info: SourceInfo, s: StampSettings, box: IntArray?, motion: Boolean, srcW: Int = info.width, srcH: Int = info.height): Pair<Int, Int> = when {
        s.transparent -> outputSize(1200, 360, s.size, motion)
        s.sticker -> stickerDims(s, box)
        else -> outputSize(srcW, srcH, s.size, motion)
    }

    private fun outFile(info: SourceInfo, ext: String): File {
        var f = File(outDir, baseName(info.name) + "." + ext)
        var i = 1
        while (f.exists()) f = File(outDir, baseName(info.name) + "-" + (i++) + "." + ext)
        return f
    }

    private fun overlayName(ext: String) = File(outDir, "t.jo7-overlay.$ext").also { it.delete() }

    suspend fun export(info: SourceInfo, s: StampSettings, onProgress: (Float) -> Unit): ExportOutput =
        withContext(Dispatchers.Default) {
            val job = currentCoroutineContext()[Job]
            val fmt = resolveFormat(info, s)
            val box = if (s.sticker && !s.transparent) stickerBox(info) else null
            val check = { job?.ensureActive() }
            try {
                when {
                    fmt == OutFormat.MP4 && info.kind == MediaKind.VIDEO && !s.transparent -> {
                        val (w, h) = frameDims(info, s, null, true)
                        val f = outFile(info, "mp4")
                        videoExporter.export(info, s, w and -2, h and -2, f, onProgress)
                        ExportOutput(f, f.name, fmt, null, 0)
                    }
                    fmt == OutFormat.GIF -> exportGif(info, s, box, onProgress, check)
                    fmt == OutFormat.WEBP && (isAnimated(info, s)) -> exportAnimWebp(info, s, box, onProgress, check)
                    else -> exportStill(info, s, fmt, box, onProgress, check)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: ExportException) { throw e }
            catch (e: OutOfMemoryError) { throw ExportException("memory", e) }
            catch (e: Exception) { throw ExportException(e.message ?: e.javaClass.simpleName, e) }
        }

    private fun isAnimated(info: SourceInfo, s: StampSettings) =
        if (s.transparent) s.mode == CreditMode.BLINK else info.isMotion || s.mode == CreditMode.BLINK

    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP

    private fun encode(b: Bitmap, fmt: Bitmap.CompressFormat, q: Int): ByteArray {
        val o = ByteArrayOutputStream(64 * 1024)
        if (!b.compress(fmt, q, o)) throw ExportException("encode")
        return o.toByteArray()
    }

    // ---------------------------------------------------------------- stills
    private fun exportStill(
        info: SourceInfo, s: StampSettings, fmt0: OutFormat, box: IntArray?, onProgress: (Float) -> Unit, check: () -> Unit,
    ): ExportOutput {
        val fmt = if (fmt0 == OutFormat.MP4 || fmt0 == OutFormat.GIF) OutFormat.PNG else fmt0
        val src: Bitmap? = if (s.transparent) null else {
            if (info.kind == MediaKind.IMAGE) SourceLoader.decodeStill(ctx, info) else SourceLoader.previewFrame(ctx, info, 0)
        }
        check()
        val (w, h) = frameDims(info, s, box, false, src?.width ?: info.width, src?.height ?: info.height)
        val flat = fmt == OutFormat.JPG && !s.transparent
        val lim = s.sticker && !s.transparent && fmt == OutFormat.WEBP
        val file = if (s.transparent) overlayName(fmt.ext) else outFile(info, fmt.ext)
        val direct = src != null && src.isMutable && !s.sticker && w == src.width && h == src.height && !(flat && src.hasAlpha())
        val bmp = if (direct) { renderer.drawCredit(Canvas(src!!), w, h, s, false); src } else renderer.renderFrame(src, w, h, s, true, flat, box)
        onProgress(0.5f)
        val bytes: ByteArray = when (fmt) {
            OutFormat.PNG -> encode(bmp, Bitmap.CompressFormat.PNG, 100)
            OutFormat.JPG -> encode(bmp, Bitmap.CompressFormat.JPEG, 95)
            else -> {
                var b: ByteArray = ByteArray(0)
                val qs = if (lim) listOf(92, 85, 75, 65, 55, 45, 35, 25, 15) else listOf(90)
                for ((i, q) in qs.withIndex()) {
                    check(); b = encode(bmp, webpFormat(), q); onProgress(0.5f + 0.5f * (i + 1) / qs.size)
                    if (!lim || b.size <= WhatsApp.STATIC_MAX) break
                }
                b
            }
        }
        file.writeBytes(bytes)
        return ExportOutput(file, file.name, fmt, null, if (lim) WhatsApp.STATIC_MAX else 0)
    }

    // ---------------------------------------------------------------- GIF
    private class Step(val t: Double, val show: Boolean, val delayMs: Int)

    private fun exportGif(
        info: SourceInfo, s: StampSettings, box: IntArray?, onProgress: (Float) -> Unit, check: () -> Unit,
    ): ExportOutput {
        val overlay = s.transparent
        val motion = info.isMotion && !overlay
        val (w, h) = frameDims(info, s, box, true)
        val file = if (overlay) overlayName("gif") else outFile(info, "gif")
        val canvasBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcStill: Bitmap? = if (!overlay && !motion) {
            if (info.kind == MediaKind.IMAGE) SourceLoader.decodeStill(ctx, info, maxOf(w, h)) else SourceLoader.previewFrame(ctx, info, maxOf(w, h))
        } else null

        fun draw(src: Bitmap?, show: Boolean): IntArray {
            renderer.renderFrame(src, w, h, s, show, true && !overlay, box, canvasBmp)
            val px = IntArray(w * h); canvasBmp.getPixels(px, 0, w, 0, 0, w, h); return px
        }

        if (!motion) {
            val steps = if (s.mode == CreditMode.STATIC) listOf(Step(0.0, true, 1000))
            else listOf(Step(0.0, false, 1000), Step(1.0, true, 1000), Step(2.0, false, 1000))
            val frames = steps.map { draw(srcStill, it.show) }
            val q = Quantizer.build(frames.filterIndexed { i, _ -> !overlay || steps[i].show }.ifEmpty { frames }, true)
            writeGif(file, w, h, q, overlay) { enc ->
                for ((i, st) in steps.withIndex()) { check(); enc(frames[i], st.delayMs); onProgress((i + 1f) / steps.size) }
            }
            return ExportOutput(file, file.name, OutFormat.GIF, null, 0)
        }

        // Motion: pass 1 learns one palette from ~14 frames, pass 2 encodes with delta frames.
        val cap = 15.0
        val full = info.durationMs / 1000.0
        val fps = 20.0
        val est = maxOf(1, if (info.kind == MediaKind.VIDEO) Math.round(minOf(full, cap) * fps).toInt() else 200)
        val stride = maxOf(1, est / 14)
        val maxSide = maxOf(w, h)
        val samples = ArrayList<IntArray>()
        SourceLoader.forEachFrame(ctx, info, cap, fps, maxSide, stride) { b, t, _ ->
            check()
            samples.add(draw(b, creditVisibleAt(s.mode, t + 1e-4))); samples.size < 16
        }
        onProgress(0.05f)
        val q = Quantizer.build(samples, true)
        samples.clear()
        var cut = false
        var count = 0
        val expected = est.toFloat()
        writeGif(file, w, h, q, false) { enc ->
            cut = SourceLoader.forEachFrame(ctx, info, cap, fps, maxSide, 1) { b, t, d ->
                check()
                enc(draw(b, creditVisibleAt(s.mode, t + 1e-4)), Math.round(d * 1000).toInt())
                count++
                onProgress(0.05f + 0.95f * minOf(1f, count / expected))
                true
            }
        }
        val kept = minOf(full, cap)
        return ExportOutput(file, file.name, OutFormat.GIF, if (cut || full > cap + 0.3) kept to full else null, 0)
    }

    /** Streams frames into a GIF, keeping only the changed rectangle of each frame (delta frames). */
    private fun writeGif(file: File, w: Int, h: Int, q: Quantizer, overlay: Boolean, produce: ((IntArray, Int) -> Unit) -> Unit) {
        BufferedOutputStream(file.outputStream(), 1 shl 16).use { os ->
            val gw = GifWriter(os, w, h, q.palette, q.transparentIndex)
            val n = w * h
            var disp: ByteArray? = null
            var pending: Triple<ByteArray, IntArray, Int>? = null // indices, rect, delay
            fun flush() {
                pending?.let { (idx, r, d) -> gw.writeFrame(idx, d, r[0], r[1], r[2], r[3], if (overlay) 2 else 1) }
                pending = null
            }
            val tol = 48
            produce { px, delay ->
                val idx = ByteArray(n); q.map(px, idx)
                if (overlay || disp == null) {
                    flush(); disp = idx.copyOf()
                    pending = Triple(idx, intArrayOf(0, 0, w, h), delay)
                } else {
                    val d0 = disp!!
                    var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
                    val chg = BooleanArray(n)
                    for (y in 0 until h) for (x in 0 until w) {
                        val o = y * w + x
                        val a = idx[o].toInt() and 0xFF; val b = d0[o].toInt() and 0xFF
                        if (a == b) continue
                        val pa = q.palette[a]; val pb = q.palette[b]
                        val dr = ((pa shr 16) and 255) - ((pb shr 16) and 255); val dg = ((pa shr 8) and 255) - ((pb shr 8) and 255); val db = (pa and 255) - (pb and 255)
                        if (dr * dr + dg * dg + db * db <= tol) continue
                        chg[o] = true
                        if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                    }
                    if (x1 < 0) {
                        pending = pending?.let { Triple(it.first, it.second, it.third + delay) }
                    } else {
                        flush()
                        val rw = x1 - x0 + 1; val rh = y1 - y0 + 1
                        val sub = ByteArray(rw * rh)
                        for (y in 0 until rh) for (x in 0 until rw) {
                            val o = (y + y0) * w + x + x0
                            if (chg[o]) { sub[y * rw + x] = idx[o]; d0[o] = idx[o] } else sub[y * rw + x] = 0
                        }
                        pending = Triple(sub, intArrayOf(x0, y0, rw, rh), delay)
                    }
                }
            }
            flush()
            gw.finish()
        }
    }

    // ---------------------------------------------------------------- animated WebP
    private class WFrame(val bmp: Bitmap, var delayMs: Int)

    private fun exportAnimWebp(
        info: SourceInfo, s: StampSettings, box: IntArray?, onProgress: (Float) -> Unit, check: () -> Unit,
    ): ExportOutput {
        val lim = s.sticker && !s.transparent
        val (w, h) = frameDims(info, s, box, true)
        val file = if (s.transparent) overlayName("webp") else outFile(info, "webp")
        val motion = info.isMotion && !s.transparent
        val maxSec = if (lim) WhatsApp.MAX_SEC else 30.0
        var frames = ArrayList<WFrame>()
        var cut = false
        val full = info.durationMs / 1000.0
        if (motion) {
            val fps = 15.0
            cut = SourceLoader.forEachFrame(ctx, info, maxSec, fps, maxOf(w, h) * 2, 1) { b, t, d ->
                check()
                val dMs = maxOf(Math.round(d * 1000).toInt(), (WhatsApp.MIN_DELAY * 1000).toInt())
                frames.add(WFrame(renderer.renderFrame(b, w, h, s, creditVisibleAt(s.mode, t + 1e-4), false, box), dMs))
                onProgress(0.25f * minOf(1f, (t / maxOf(1.0, minOf(full, maxSec))).toFloat()))
                // keep within a memory budget by merging frames as we go
                if (frames.size.toLong() * w * h * 4 > 96L * 1024 * 1024) frames = merge(frames)
                true
            }
        } else {
            val still: Bitmap? = if (s.transparent) null else if (info.kind == MediaKind.IMAGE) SourceLoader.decodeStill(ctx, info, maxOf(w, h) * 2) else SourceLoader.previewFrame(ctx, info, maxOf(w, h) * 2)
            for (show in listOf(false, true, false)) frames.add(WFrame(renderer.renderFrame(still, w, h, s, show, false, box), 1000))
        }
        check()
        val qList = if (lim) listOf(80, 65, 50, 38, 28) else listOf(85)
        var best: ByteArrayOutputStream? = null
        var work: ArrayList<WFrame> = frames
        outer@ for (round in 0 until 4) {
            for ((qi, q) in qList.withIndex()) {
                check()
                val mux = AnimatedWebp.Builder(w, h)
                var over = false
                for ((i, f) in work.withIndex()) {
                    mux.addFrame(encode(f.bmp, webpFormat(), q), f.delayMs)
                    if (lim && mux.payloadBytes > WhatsApp.ANIM_MAX * 1.5 && !(round == 3 && qi == qList.size - 1)) { over = true; break }
                    if (i % 6 == 5) { check(); onProgress(0.25f + 0.75f * ((round * qList.size + qi + (i + 1f) / work.size) / (4f * qList.size))) }
                }
                if (over) continue
                val bo = ByteArrayOutputStream(); mux.writeTo(bo); best = bo
                if (!lim || bo.size() <= WhatsApp.ANIM_MAX) break@outer
            }
            if (work.size <= 2) break
            work = merge(work)
        }
        val out = best ?: throw ExportException("webp")
        file.writeBytes(out.toByteArray())
        val keptCut = if (motion && (cut || full > maxSec + 0.3)) minOf(full, maxSec) to full else null
        return ExportOutput(file, file.name, OutFormat.WEBP, keptCut, if (lim) WhatsApp.ANIM_MAX else 0)
    }

    /** Halve the frame count by dropping every second frame and adding its delay to the previous one. */
    private fun merge(src: List<WFrame>): ArrayList<WFrame> {
        val out = ArrayList<WFrame>()
        var i = 0
        while (i < src.size) {
            val f = src[i]
            if (i + 1 < src.size) { f.delayMs += src[i + 1].delayMs; src[i + 1].bmp.recycle() }
            out.add(f); i += 2
        }
        return out
    }

    fun clearOutputs() { outDir.listFiles()?.forEach { it.delete() } }
}

/** Preview rendering shared by the UI (same renderer as the export). */
fun MediaEngine.previewRender(base: Bitmap?, s: StampSettings, show: Boolean): Bitmap {
    val r = this.renderer
    if (s.transparent) {
        val bmp = r.renderFrame(null, 1200, 360, s, show, false, null)
        return bmp
    }
    if (base == null) return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    if (s.sticker) {
        val box = StampRenderer.contentBox(base)
        val (w, h) = if (s.stickerCrop && box != null) {
            val bw = maxOf(1, box[2] - box[0]); val bh = maxOf(1, box[3] - box[1]); val k = 512.0 / maxOf(bw, bh)
            maxOf(1, Math.round(bw * k).toInt()) to maxOf(1, Math.round(bh * k).toInt())
        } else 512 to 512
        return r.renderFrame(base, w, h, s, show, false, box)
    }
    return r.renderFrame(base, base.width, base.height, s, show, false, null)
}
