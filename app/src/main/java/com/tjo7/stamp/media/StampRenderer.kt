package com.tjo7.stamp.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.tjo7.stamp.domain.StampGeometry
import com.tjo7.stamp.domain.StampSettings
import com.tjo7.stamp.domain.TextMetricsPx

/**
 * Draws the credit. The same code path serves the preview and every export, so what the user sees is what is saved.
 * [regular] / [bold] are the bundled Poppins faces (system fallback covers glyphs Poppins lacks, e.g. Arabic).
 */
class StampRenderer(private val regular: Typeface, private val bold: Typeface) {

    private fun paintFor(s: StampSettings, fontSize: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = if (s.bold) bold else regular
        textSize = fontSize
        color = s.color
    }

    /** Measures the text for a frame width; also returns the font size that fits the requested width fraction. */
    private fun measure(s: StampSettings, frameW: Int, transparent: Boolean): Pair<TextMetricsPx, Float> {
        val t = s.text.trim()
        val probe = paintFor(s, 100f)
        val base = probe.measureText(if (t.isEmpty()) " " else t)
        val frac = if (transparent) 80f else s.widthPct.toFloat()
        val fs = StampGeometry.fontSizeFor(frameW, frac, base)
        val p = paintFor(s, fs)
        val r = Rect()
        p.getTextBounds(if (t.isEmpty()) " " else t, 0, if (t.isEmpty()) 1 else t.length, r)
        return TextMetricsPx(p.measureText(if (t.isEmpty()) " " else t), (-r.top).toFloat(), r.bottom.toFloat()) to fs
    }

    /** Height of the strip the credit needs at the top/bottom of a sticker. */
    fun stripFor(s: StampSettings, w: Int): Float {
        if (s.text.isBlank()) return 0f
        val (m, fs) = measure(s, w, false)
        val lw = if (s.weight >= 600) fs * 0.04f else 0f
        val pad = StampGeometry.padding(w, s.marginPct, lw, fs, s.shadow / 100f)
        return m.ascent + m.descent + pad * 2
    }

    /** Draws only the credit onto [canvas] (no background). Returns false when nothing was drawn. */
    fun drawCredit(canvas: Canvas, w: Int, h: Int, s: StampSettings, transparent: Boolean): Boolean {
        val t = s.text.trim()
        if (t.isEmpty()) return false
        val (m, fs) = measure(s, w, transparent)
        val pl = StampGeometry.place(w, h, s, m, fs, transparent)
        val amt = s.shadow / 100f
        val paint = paintFor(s, fs)
        val layer = canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (s.opacity * 255 / 100).coerceIn(0, 255))
        if (amt > 0f) {
            val a = (255 * minOf(1f, 0.3f + 0.7f * amt)).toInt()
            val shadowColor = if (s.white) Color.argb(a, 0, 0, 0) else Color.argb(a, 255, 255, 255)
            paint.setShadowLayer(fs * (0.04f + 0.2f * amt), 0f, fs * 0.03f, shadowColor)
        }
        if (pl.strokeWidth > 0f) {
            val stroke = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = pl.strokeWidth; strokeJoin = Paint.Join.ROUND }
            canvas.drawText(t, pl.x, pl.y, stroke)
        }
        paint.style = Paint.Style.FILL
        canvas.drawText(t, pl.x, pl.y, paint)
        canvas.restoreToCount(layer)
        return true
    }

    /** A transparent bitmap holding just the credit (used as the video overlay). */
    fun creditLayer(w: Int, h: Int, s: StampSettings): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawCredit(Canvas(bmp), w, h, s, false)
        return bmp
    }

    /**
     * Composes one output frame: background (optional white), the source picture and the credit.
     * [box] is the sticker content box (x0,y0,x1,y1 in source pixels) when relevant.
     */
    fun renderFrame(
        src: Bitmap?, w: Int, h: Int, s: StampSettings, showCredit: Boolean,
        flat: Boolean, box: IntArray?, into: Bitmap? = null,
    ): Bitmap {
        val out = into ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        out.eraseColor(if (flat) Color.WHITE else Color.TRANSPARENT)
        val pic = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (!s.transparent && src != null) {
            if (s.sticker && s.stickerCrop && box != null) {
                c.drawBitmap(src, Rect(box[0], box[1], box[2], box[3]), RectF(0f, 0f, w.toFloat(), h.toFloat()), pic)
            } else if (s.sticker) {
                val strip = if (showCredit) stripFor(s, w) else 0f
                val (k, ox, oy) = StampGeometry.stickerFit(w, h, src.width, src.height, box, strip, s.corner.top)
                c.drawBitmap(src, null, RectF(ox, oy, ox + src.width * k, oy + src.height * k), pic)
            } else {
                c.drawBitmap(src, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), pic)
            }
        }
        if (showCredit) drawCredit(c, w, h, s, s.transparent)
        return out
    }

    companion object {
        /** Bounding box of pixels with alpha > 24, or null when the picture is fully transparent. */
        fun contentBox(bmp: Bitmap, maxScan: Int = 256): IntArray? {
            val k = minOf(1f, maxScan / maxOf(bmp.width, bmp.height).toFloat())
            val sw = maxOf(1, Math.round(bmp.width * k)); val sh = maxOf(1, Math.round(bmp.height * k))
            val small = if (k < 1f) Bitmap.createScaledBitmap(bmp, sw, sh, true) else bmp
            val px = IntArray(sw * sh)
            small.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (small !== bmp) small.recycle()
            var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = -1; var y1 = -1
            for (y in 0 until sh) for (x in 0 until sw) if ((px[y * sw + x] ushr 24) > 24) {
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
            if (x1 < 0) return null
            return intArrayOf(
                Math.floor(x0 / k.toDouble()).toInt().coerceAtLeast(0), Math.floor(y0 / k.toDouble()).toInt().coerceAtLeast(0),
                Math.ceil((x1 + 1) / k.toDouble()).toInt().coerceAtMost(bmp.width), Math.ceil((y1 + 1) / k.toDouble()).toInt().coerceAtMost(bmp.height),
            )
        }

        fun hasAlpha(bmp: Bitmap): Boolean {
            if (!bmp.hasAlpha()) return false
            val s = Bitmap.createScaledBitmap(bmp, 48, 48, true)
            val px = IntArray(48 * 48); s.getPixels(px, 0, 48, 0, 0, 48, 48); s.recycle()
            return px.any { (it ushr 24) < 250 }
        }
    }
}
