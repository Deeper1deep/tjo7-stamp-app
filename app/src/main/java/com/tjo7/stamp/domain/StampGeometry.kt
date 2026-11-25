package com.tjo7.stamp.domain

/**
 * Pure placement maths for the credit text, independent of Android classes so that it can be unit tested.
 * All coordinates are for a frame of [w] x [h] pixels; (x, y) is the text BASELINE origin.
 */
data class TextMetricsPx(val width: Float, val ascent: Float, val descent: Float)

data class Placement(
    val x: Float, val y: Float, val fontSize: Float, val strokeWidth: Float,
    /** Allowed offset range as fractions of the frame, so the credit never leaves it. */
    val limX0: Float, val limX1: Float, val limY0: Float, val limY1: Float,
)

object StampGeometry {
    /** Font size that makes the text [fracPct] percent of the frame width, given its width at [refSize]. */
    fun fontSizeFor(frameW: Int, fracPct: Float, widthAtRef: Float, refSize: Float = 100f): Float {
        val base = if (widthAtRef > 0f) widthAtRef else 1f
        return refSize * (frameW * fracPct / 100f) / base
    }

    fun padding(frameW: Int, marginPct: Int, strokeW: Float, fontSize: Float, shadowAmt: Float) =
        frameW * marginPct / 100f + strokeW / 2f + fontSize * 0.2f * shadowAmt

    fun place(
        w: Int, h: Int, s: StampSettings, m: TextMetricsPx, fontSize: Float, transparent: Boolean,
    ): Placement {
        val lw = if (s.weight >= 600) fontSize * 0.04f else 0f
        val amt = s.shadow / 100f
        val pad = padding(w, s.marginPct, lw, fontSize, amt)
        if (transparent) {
            return Placement((w - m.width) / 2f, (h + m.ascent - m.descent) / 2f, fontSize, lw, 0f, 0f, 0f, 0f)
        }
        var x = if (s.corner.left) pad else w - pad - m.width
        var y = if (s.corner.top) pad + m.ascent else h - pad - m.descent
        val lim = floatArrayOf(-x / w, (w - m.width - x) / w, (m.ascent - y) / h, (h - m.descent - y) / h)
        x += (s.dx * w / 100f).coerceIn(-x, w - m.width - x)
        y += (s.dy * h / 100f).coerceIn(m.ascent - y, h - m.descent - y)
        return Placement(x, y, fontSize, lw, lim[0], lim[1], lim[2], lim[3])
    }

    /**
     * Sticker fit: scale the picture so a credit placed in the top/bottom strip never overlaps content.
     * Returns (scale, offsetX, offsetY) for drawing a [srcW]x[srcH] bitmap into [w]x[h] given the
     * content box (x0,y0,x1,y1) in source pixels.
     */
    fun stickerFit(
        w: Int, h: Int, srcW: Int, srcH: Int, box: IntArray?, needStrip: Float, top: Boolean,
    ): Triple<Float, Float, Float> {
        val k0 = minOf(w / srcW.toFloat(), h / srcH.toFloat())
        var k = k0
        var ox = (w - srcW * k0) / 2f
        var oy = (h - srcH * k0) / 2f
        if (box != null && needStrip > 0f) {
            val free = if (top) oy + box[1] * k0 else h - (oy + box[3] * k0)
            if (free < needStrip) {
                val bh = (box[3] - box[1]).toFloat()
                k = minOf(k0, (h - needStrip) / bh)
                val mid = (box[1] + box[3]) / 2f * k
                val regionMid = if (top) needStrip + (h - needStrip) / 2f else (h - needStrip) / 2f
                ox = (w - srcW * k) / 2f
                oy = regionMid - mid
            }
        }
        return Triple(k, ox, oy)
    }
}
