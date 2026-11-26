package com.tjo7.stamp.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.tjo7.stamp.domain.MediaKind
import java.io.File
import java.io.InputStream

/** A sticker pack the item came from (so its non-image files travel with the output zip). */
class PackRef(val name: String, val others: List<Pair<String, File>>)

/** Everything the app needs to know about one imported file. */
data class SourceInfo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val ext: String,
    val kind: MediaKind,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val hasAudio: Boolean,
    val sizeBytes: Long,
    val hasAlpha: Boolean,
    val bitrate: Int = 0,
    val pack: PackRef? = null,
    val entry: String? = null,
) {
    val isMotion get() = kind != MediaKind.IMAGE
}

class UnsupportedMediaException(msg: String) : Exception(msg)

object SourceLoader {
    const val MAX_PIXELS = 40_000_000
    const val MAX_BYTES_IN_MEMORY = 96L * 1024 * 1024
    private val VIDEO_EXT = setOf("mp4", "webm", "mov", "m4v", "3gp", "mkv")

    fun extOf(name: String) = name.substringAfterLast('.', "").lowercase()

    fun displayName(ctx: Context, uri: Uri): Pair<String, Long> {
        if (uri.scheme == "file") { val f = File(uri.path!!); return f.name to f.length() }
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = -1L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Exception) { }
        return name to size
    }

    fun open(ctx: Context, uri: Uri): InputStream =
        ctx.contentResolver.openInputStream(uri) ?: throw UnsupportedMediaException("cannot open")

    private fun head(ctx: Context, uri: Uri, n: Int = 32): ByteArray = open(ctx, uri).use { s ->
        val b = ByteArray(n); var r = 0
        while (r < n) { val k = s.read(b, r, n - r); if (k <= 0) break; r += k }
        b.copyOf(r)
    }

    private fun isGifMagic(h: ByteArray) = h.size >= 6 && String(h, 0, 3, Charsets.US_ASCII) == "GIF"

    /** Read basic facts about [uri]. Throws [UnsupportedMediaException] when it is not usable media. */
    fun probe(ctx: Context, uri: Uri, id: Long, pack: PackRef? = null, entry: String? = null, nameOverride: String? = null): SourceInfo {
        val (n0, size) = displayName(ctx, uri)
        val name = nameOverride ?: n0
        val ext = extOf(name)
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        val h = head(ctx, uri)
        val isGif = isGifMagic(h)
        val isWebp = h.size >= 12 && AnimatedWebp.isWebp(h)
        val isVideo = !isGif && !isWebp && (mime.startsWith("video/") || ext in VIDEO_EXT) && !mime.startsWith("image/")

        if (isVideo) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                var hh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) { val t = w; w = hh; hh = t }
                val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                val br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                if (w <= 0 || hh <= 0) throw UnsupportedMediaException("video size")
                return SourceInfo(id, uri, name, ext, MediaKind.VIDEO, w, hh, dur, audio, size, false, br, pack, entry)
            } catch (e: UnsupportedMediaException) { throw e } catch (e: Exception) {
                throw UnsupportedMediaException("video: " + e.message)
            } finally { try { r.release() } catch (_: Exception) { } }
        }
        if (isGif) {
            val bytes = readAll(ctx, uri, size)
            val hd = GifDecoder.readHeader(bytes)
            val sc = GifDecoder.scan(bytes)
            val kind = if (sc.frames > 1) MediaKind.ANIM_GIF else MediaKind.IMAGE
            return SourceInfo(id, uri, name, ext.ifEmpty { "gif" }, kind, hd.width, hd.height, sc.totalMs.toLong(), false, size, true, 0, pack, entry)
        }
        if (isWebp) {
            val bytes = readAll(ctx, uri, size)
            if (AnimatedWebp.isAnimated(bytes)) {
                val a = AnimatedWebp.parse(bytes)
                val dur = a.frames.sumOf { maxOf(it.durationMs, 20) }.toLong()
                return SourceInfo(id, uri, name, ext.ifEmpty { "webp" }, MediaKind.ANIM_WEBP, a.width, a.height, dur, false, size, true, 0, pack, entry)
            }
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(ctx, uri).use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw UnsupportedMediaException("not an image")
        var w = opts.outWidth; var hh = opts.outHeight
        val orient = exifOrientation(ctx, uri)
        if (orient == ExifInterface.ORIENTATION_ROTATE_90 || orient == ExifInterface.ORIENTATION_ROTATE_270 ||
            orient == ExifInterface.ORIENTATION_TRANSPOSE || orient == ExifInterface.ORIENTATION_TRANSVERSE) { val t = w; w = hh; hh = t }
        val alpha = opts.outMimeType == "image/png" || opts.outMimeType == "image/webp"
        return SourceInfo(id, uri, name, ext, MediaKind.IMAGE, w, hh, 0, false, size, alpha, 0, pack, entry)
    }

    fun readAll(ctx: Context, uri: Uri, knownSize: Long): ByteArray {
        if (knownSize > MAX_BYTES_IN_MEMORY) throw UnsupportedMediaException("file too large for animation")
        return open(ctx, uri).use { it.readBytes() }.also { if (it.size > MAX_BYTES_IN_MEMORY) throw UnsupportedMediaException("file too large") }
    }

    private fun exifOrientation(ctx: Context, uri: Uri): Int = try {
        open(ctx, uri).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }

    /** Decodes a still image upright. [maxSide] (>0) lets the decoder down-sample to save memory (preview, thumbnails). */
    fun decodeStill(ctx: Context, info: SourceInfo, maxSide: Int = 0): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(ctx, info.uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val bw = bounds.outWidth; val bh = bounds.outHeight
        if (maxSide > 0) while (maxOf(bw, bh) / (sample * 2) >= maxSide) sample *= 2
        while (bw.toLong() * bh / (sample.toLong() * sample) > MAX_PIXELS) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = open(ctx, info.uri).use { BitmapFactory.decodeStream(it, null, opts) } ?: throw UnsupportedMediaException("decode failed")
        val m = Matrix()
        when (exifOrientation(ctx, info.uri)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return raw
        }
        val rot = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (rot !== raw) raw.recycle()
        return rot
    }

    /**
     * Streams every frame of a GIF / animated WebP / video to [onFrame] as (bitmap, startSeconds, durationSeconds).
     * The bitmap is reused between calls. [maxSec] caps the clip; [videoFps] drives video sampling.
     * Returns true when the clip was cut because of [maxSec].
     */
    fun forEachFrame(
        ctx: Context, info: SourceInfo, maxSec: Double, videoFps: Double, maxSide: Int = 0, stride: Int = 1,
        onFrame: (Bitmap, Double, Double) -> Boolean,
    ): Boolean {
        return when (info.kind) {
            MediaKind.ANIM_GIF -> gifFrames(ctx, info, maxSec, stride, onFrame)
            MediaKind.ANIM_WEBP -> webpFrames(ctx, info, maxSec, stride, onFrame)
            MediaKind.VIDEO -> videoFrames(ctx, info, maxSec, videoFps, maxSide, stride, onFrame)
            MediaKind.IMAGE -> { onFrame(decodeStill(ctx, info, maxSide), 0.0, 1.0); false }
        }
    }

    private fun gifFrames(ctx: Context, info: SourceInfo, maxSec: Double, stride: Int, onFrame: (Bitmap, Double, Double) -> Boolean): Boolean {
        val bytes = readAll(ctx, info.uri, info.sizeBytes)
        val hd = GifDecoder.readHeader(bytes)
        val bmp = Bitmap.createBitmap(hd.width, hd.height, Bitmap.Config.ARGB_8888)
        var acc = 0.0; var cut = false
        GifDecoder.decode(bytes) { px, delayMs, index ->
            val d = delayMs / 1000.0
            if (acc + 1e-6 >= maxSec) { cut = true; return@decode false }
            var cont = true
            if (index % stride == 0) {
                bmp.setPixels(px, 0, hd.width, 0, 0, hd.width, hd.height)
                cont = onFrame(bmp, acc, d)
            }
            acc += d
            cont
        }
        return cut
    }

    private fun webpFrames(ctx: Context, info: SourceInfo, maxSec: Double, stride: Int, onFrame: (Bitmap, Double, Double) -> Boolean): Boolean {
        val anim = AnimatedWebp.parse(readAll(ctx, info.uri, info.sizeBytes))
        val canvasBmp = Bitmap.createBitmap(anim.width, anim.height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(canvasBmp)
        val clear = android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }
        var acc = 0.0; var cut = false
        for ((fi, f) in anim.frames.withIndex()) {
            if (acc + 1e-6 >= maxSec) { cut = true; break }
            val still = AnimatedWebp.standalone(f.chunks, f.w, f.h, true)
            val fb = BitmapFactory.decodeByteArray(still, 0, still.size) ?: throw UnsupportedMediaException("webp frame")
            if (!f.blend) cv.drawRect(f.x.toFloat(), f.y.toFloat(), (f.x + f.w).toFloat(), (f.y + f.h).toFloat(), clear)
            cv.drawBitmap(fb, f.x.toFloat(), f.y.toFloat(), null)
            fb.recycle()
            val d = maxOf(f.durationMs, 20) / 1000.0
            val cont = if (fi % stride == 0) onFrame(canvasBmp, acc, d) else true
            acc += d
            if (f.disposeBg) cv.drawRect(f.x.toFloat(), f.y.toFloat(), (f.x + f.w).toFloat(), (f.y + f.h).toFloat(), clear)
            if (!cont) break
        }
        return cut
    }

    private fun videoFrames(
        ctx: Context, info: SourceInfo, maxSec: Double, fps: Double, maxSide: Int, stride: Int,
        onFrame: (Bitmap, Double, Double) -> Boolean,
    ): Boolean {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, info.uri)
            val total = info.durationMs / 1000.0
            val len = minOf(total, maxSec)
            val dt = 1.0 / fps
            val n = maxOf(1, Math.round(len / dt).toInt())
            for (i in 0 until n step stride) {
                val t = i * dt
                val fr = r.getFrameAtTime((t * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST) ?: r.getFrameAtTime(0) ?: break
                val scaled = if (maxSide > 0 && maxOf(fr.width, fr.height) > maxSide) {
                    val k = maxSide / maxOf(fr.width, fr.height).toFloat()
                    Bitmap.createScaledBitmap(fr, maxOf(1, (fr.width * k).toInt()), maxOf(1, (fr.height * k).toInt()), true).also { if (it !== fr) fr.recycle() }
                } else fr
                val cont = onFrame(scaled, t, dt)
                scaled.recycle()
                if (!cont) break
            }
            return total > maxSec + 0.05
        } finally { try { r.release() } catch (_: Exception) { } }
    }

    /** One representative frame for previews/thumbnails (video: ~1 s in so a blink-visible moment shows). */
    fun previewFrame(ctx: Context, info: SourceInfo, maxSide: Int, atMs: Long = -1L): Bitmap = when (info.kind) {
        MediaKind.IMAGE -> decodeStill(ctx, info, maxSide)
        MediaKind.VIDEO -> {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, info.uri)
                val t = if (atMs >= 0) atMs * 1000 else minOf(1_000_000L, info.durationMs * 500)
                val fr = r.getFrameAtTime(t, if (atMs >= 0) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.getFrameAtTime(0) ?: throw UnsupportedMediaException("frame")
                downscale(fr, maxSide)
            } finally { try { r.release() } catch (_: Exception) { } }
        }
        else -> {
            var first: Bitmap? = null
            forEachFrame(ctx, info, 1.0, 1.0, maxSide) { b, _, _ -> first = b.copy(Bitmap.Config.ARGB_8888, false); false }
            downscale(first ?: throw UnsupportedMediaException("frame"), maxSide)
        }
    }

    private fun downscale(b: Bitmap, maxSide: Int): Bitmap {
        if (maxSide <= 0 || maxOf(b.width, b.height) <= maxSide) return b
        val k = maxSide / maxOf(b.width, b.height).toFloat()
        return Bitmap.createScaledBitmap(b, maxOf(1, (b.width * k).toInt()), maxOf(1, (b.height * k).toInt()), true).also { if (it !== b) b.recycle() }
    }
}
