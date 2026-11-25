package com.tjo7.stamp

import com.tjo7.stamp.domain.*
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun defaultsMatchTheOldApp() {
        val s = StampSettings()
        assertEquals("@t.jo7", s.text); assertEquals(Corner.BL, s.corner); assertEquals(13, s.widthPct); assertEquals(2, s.marginPct)
        assertEquals(55, s.opacity); assertEquals(0, s.shadow); assertTrue(s.white); assertEquals(CreditMode.STATIC, s.mode)
        assertEquals(0f, s.dx, 0f); assertEquals(OutFormat.AUTO, s.format); assertEquals(SizeSel.ORIG, s.size); assertTrue(s.keepAudio)
    }

    @Test fun presets() {
        val b = StampSettings().applyPreset(Preset.BLINK)
        assertEquals(CreditMode.BLINK, b.mode); assertEquals(24, b.widthPct); assertEquals(3, b.marginPct); assertEquals(100, b.opacity); assertTrue(b.bold)
        val c = b.applyPreset(Preset.CREDIT)
        assertEquals(CreditMode.STATIC, c.mode); assertEquals(13, c.widthPct); assertEquals(55, c.opacity); assertFalse(c.bold)
    }

    @Test fun sanitizeClamps() {
        val s = StampSettings(text = "x".repeat(200), widthPct = 900, marginPct = -5, opacity = 500, shadow = -1, dx = 999f).sanitized()
        assertEquals(60, s.text.length); assertEquals(100, s.widthPct); assertEquals(0, s.marginPct); assertEquals(100, s.opacity); assertEquals(0, s.shadow); assertEquals(100f, s.dx, 0f)
    }

    @Test fun legacyWebmMapsToMp4() {
        assertEquals(OutFormat.MP4, OutFormat.fromLegacy("webm")); assertEquals(OutFormat.GIF, OutFormat.fromLegacy("gif")); assertNull(OutFormat.fromLegacy("bogus"))
    }

    @Test fun blinkShowsInSecondSecondOfThree() {
        assertTrue(creditVisibleAt(CreditMode.STATIC, 0.0))
        assertFalse(creditVisibleAt(CreditMode.BLINK, 0.5)); assertTrue(creditVisibleAt(CreditMode.BLINK, 1.5)); assertFalse(creditVisibleAt(CreditMode.BLINK, 2.5))
        assertTrue(creditVisibleAt(CreditMode.BLINK, 4.5)); assertFalse(creditVisibleAt(CreditMode.BLINK, 3.2))
    }

    @Test fun outputSizeNeverUpscalesAndCapsMotion() {
        assertEquals(1000 to 500, outputSize(1000, 500, SizeSel.ORIG, false))
        assertEquals(720 to 360, outputSize(1000, 500, SizeSel.W720, false))
        assertEquals(1000 to 500, outputSize(1000, 500, SizeSel.W1080, false))
        val (w, h) = outputSize(4000, 3000, SizeSel.ORIG, true)
        assertTrue(w.toLong() * h <= 8_400_000L)
        assertEquals(4 / 3.0, w / h.toDouble(), 0.01)
    }

    @Test fun placementCornersAndClamp() {
        val m = TextMetricsPx(100f, 20f, 5f)
        val s = StampSettings(widthPct = 10, marginPct = 2)
        val fs = 30f
        val bl = StampGeometry.place(1000, 500, s.copy(corner = Corner.BL), m, fs, false)
        assertEquals(20f, bl.x, 0.01f); assertEquals(500f - 10f - 5f, bl.y, 0.01f)
        val tr = StampGeometry.place(1000, 500, s.copy(corner = Corner.TR), m, fs, false)
        assertEquals(1000f - 20f - 100f, tr.x, 0.01f); assertEquals(10f + 20f, tr.y, 0.01f)
        val far = StampGeometry.place(1000, 500, s.copy(corner = Corner.BL, dx = 100f, dy = -100f), m, fs, false)
        assertTrue(far.x + 100f <= 1000.01f); assertTrue(far.y - 20f >= -0.01f)
        val t = StampGeometry.place(1000, 360, s, m, fs, true)
        assertEquals(450f, t.x, 0.01f)
    }

    @Test fun boldAddsStroke() {
        val m = TextMetricsPx(100f, 20f, 5f)
        assertEquals(0f, StampGeometry.place(500, 500, StampSettings(), m, 50f, false).strokeWidth, 0f)
        assertEquals(2f, StampGeometry.place(500, 500, StampSettings(bold = true), m, 50f, false).strokeWidth, 0.001f)
    }

    @Test fun stickerFitShrinksWhenNoRoomForCredit() {
        val box = intArrayOf(0, 0, 512, 512)
        val (k, _, _) = StampGeometry.stickerFit(512, 512, 512, 512, box, 80f, false)
        assertTrue(k < 1f)
        val (k2, _, _) = StampGeometry.stickerFit(512, 512, 512, 512, intArrayOf(100, 100, 300, 300), 80f, false)
        assertEquals(1f, k2, 0.001f)
    }

    @Test fun fontSizeFitsWidthFraction() {
        // at 100px the text is 400px wide; we want 13% of 1000px = 130px  => 32.5px
        assertEquals(32.5f, StampGeometry.fontSizeFor(1000, 13f, 400f), 0.01f)
    }
}
