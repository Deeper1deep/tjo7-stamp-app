package com.tjo7.stamp

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjo7.stamp.domain.*
import com.tjo7.stamp.media.StampRenderer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RendererInstrumentedTest {
    private val r = Support.renderer()

    private fun solid(w: Int, h: Int, c: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(c) }

    private fun changed(a: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int, base: Int): Int {
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) if (a.getPixel(x, y) != base) n++
        return n
    }

    @Test fun creditLandsInTheChosenCorner() {
        val base = Color.rgb(20, 40, 160)
        for (c in Corner.entries) {
            val out = r.renderFrame(solid(600, 400, base), 600, 400, StampSettings(corner = c, opacity = 100, widthPct = 20), true, false, null)
            val q = when (c) { Corner.TL -> intArrayOf(0, 0, 300, 200); Corner.TR -> intArrayOf(300, 0, 600, 200); Corner.BL -> intArrayOf(0, 200, 300, 400); Corner.BR -> intArrayOf(300, 200, 600, 400) }
            val other = intArrayOf(0, 0, 600, 400)
            val inQ = changed(out, q[0], q[1], q[2], q[3], base)
            val total = changed(out, other[0], other[1], other[2], other[3], base)
            assertTrue("corner $c has text", inQ > 50)
            assertEquals("corner $c: all changed pixels are in its quadrant", total, inQ)
        }
    }

    @Test fun opacityAndColorAreApplied() {
        val base = Color.BLACK
        val full = r.renderFrame(solid(600, 300, base), 600, 300, StampSettings(opacity = 100, widthPct = 30), true, false, null)
        val half = r.renderFrame(solid(600, 300, base), 600, 300, StampSettings(opacity = 50, widthPct = 30), true, false, null)
        fun maxLum(b: Bitmap): Int { var m = 0; for (y in 0 until b.height) for (x in 0 until b.width) m = maxOf(m, Color.red(b.getPixel(x, y))); return m }
        assertTrue(maxLum(full) >= 250)
        assertTrue(maxLum(half) in 100..160)
        val black = r.renderFrame(solid(600, 300, Color.WHITE), 600, 300, StampSettings(white = false, opacity = 100, widthPct = 30), true, false, null)
        var minLum = 255; for (y in 0 until 300) for (x in 0 until 600) minLum = minOf(minLum, Color.red(black.getPixel(x, y)))
        assertTrue(minLum < 10)
    }

    @Test fun hiddenCreditLeavesPictureUntouched() {
        val base = Color.rgb(1, 2, 3)
        val out = r.renderFrame(solid(300, 200, base), 300, 200, StampSettings(), false, false, null)
        assertEquals(0, changed(out, 0, 0, 300, 200, base))
    }

    @Test fun transparentOverlayHasAlphaOnlyForText() {
        val out = r.renderFrame(null, 1200, 360, StampSettings(opacity = 100, transparent = true), true, false, null)
        var opaque = 0; var clear = 0
        for (y in 0 until 360 step 2) for (x in 0 until 1200 step 2) { if (Color.alpha(out.getPixel(x, y)) > 200) opaque++ else if (out.getPixel(x, y) == 0) clear++ }
        assertTrue(opaque > 100); assertTrue(clear > opaque)
        assertEquals(0, out.getPixel(2, 2))
    }

    @Test fun moveOffsetShiftsTheCredit() {
        val base = Color.BLACK
        fun leftmost(s: StampSettings): Int {
            val o = r.renderFrame(solid(600, 300, base), 600, 300, s, true, false, null)
            for (x in 0 until 600) for (y in 0 until 300) if (o.getPixel(x, y) != base) return x
            return -1
        }
        val a = leftmost(StampSettings(opacity = 100)); val b = leftmost(StampSettings(opacity = 100, dx = 20f))
        assertTrue("dx moves right: $a -> $b", b > a + 60)
        val far = leftmost(StampSettings(opacity = 100, dx = 500f))
        assertTrue("never leaves the frame", far in 0 until 600)
    }

    @Test fun contentBoxFindsOpaqueRegion() {
        val b = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        for (y in 20 until 60) for (x in 50 until 150) b.setPixel(x, y, Color.RED)
        val box = StampRenderer.contentBox(b)!!
        assertEquals(50.0, box[0].toDouble(), 2.0); assertEquals(20.0, box[1].toDouble(), 2.0); assertEquals(150.0, box[2].toDouble(), 2.0); assertEquals(60.0, box[3].toDouble(), 2.0)
        assertNull(StampRenderer.contentBox(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)))
        assertTrue(StampRenderer.hasAlpha(b)); 
    }

    @Test fun arabicTextRendersWithFallbackFont() {
        val out = r.renderFrame(solid(600, 300, Color.BLACK), 600, 300, StampSettings(text = "حقوق المصمم", opacity = 100, widthPct = 40), true, false, null)
        assertTrue(changed(out, 0, 0, 600, 300, Color.BLACK) > 100)
    }
}
