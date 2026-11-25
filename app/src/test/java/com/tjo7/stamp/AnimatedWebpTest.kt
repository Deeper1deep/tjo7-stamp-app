package com.tjo7.stamp

import com.tjo7.stamp.media.AnimatedWebp
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class AnimatedWebpTest {
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** Minimal still WebP: RIFF + VP8 chunk with [n] payload bytes (not decodable, structure only). */
    private fun fakeStill(n: Int, alpha: Boolean = false): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray())
        if (alpha) { body.write("ALPH".toByteArray()); body.write(le32(3)); body.write(byteArrayOf(1, 2, 3, 0)) }
        body.write("VP8 ".toByteArray()); body.write(le32(n)); body.write(ByteArray(n) { 7 }); if (n % 2 == 1) body.write(0)
        val o = ByteArrayOutputStream(); o.write("RIFF".toByteArray()); o.write(le32(body.size())); o.write(body.toByteArray())
        return o.toByteArray()
    }

    @Test fun detectsStillVsAnimated() {
        assertTrue(AnimatedWebp.isWebp(fakeStill(10)))
        assertFalse(AnimatedWebp.isAnimated(fakeStill(10)))
        assertFalse(AnimatedWebp.isWebp(ByteArray(40)))
    }

    @Test fun muxAndParseRoundTrip() {
        val b = AnimatedWebp.Builder(64, 48)
        b.addFrame(fakeStill(11), 100); b.addFrame(fakeStill(20, alpha = true), 250); b.addFrame(fakeStill(8), 5)
        val o = ByteArrayOutputStream(); b.writeTo(o)
        val bytes = o.toByteArray()
        assertTrue(AnimatedWebp.isAnimated(bytes))
        // RIFF size field must match the real length
        val riff = (bytes[4].toInt() and 255) or ((bytes[5].toInt() and 255) shl 8) or ((bytes[6].toInt() and 255) shl 16) or ((bytes[7].toInt() and 255) shl 24)
        assertEquals(bytes.size - 8, riff)
        val a = AnimatedWebp.parse(bytes)
        assertEquals(64, a.width); assertEquals(48, a.height); assertEquals(3, a.frames.size)
        assertEquals(100, a.frames[0].durationMs); assertEquals(250, a.frames[1].durationMs); assertEquals(8, a.frames[2].durationMs)
        assertEquals(64, a.frames[1].w); assertEquals(48, a.frames[1].h)
        assertFalse(a.frames[0].blend)
        // frame 1 carries ALPH + VP8 chunks
        assertTrue(String(a.frames[1].chunks, 0, 4) == "ALPH")
    }

    @Test fun standaloneWrapsChunks() {
        val chunks = AnimatedWebp.imageChunks(fakeStill(9))
        val s = AnimatedWebp.standalone(chunks, 10, 12, true)
        assertTrue(AnimatedWebp.isWebp(s)); assertFalse(AnimatedWebp.isAnimated(s))
    }

    @Test fun rejectsTruncated() {
        val s = fakeStill(100)
        try { AnimatedWebp.imageChunks(s.copyOf(30)); fail() } catch (e: AnimatedWebp.WebpException) { }
    }
}
