package com.tjo7.stamp

import com.tjo7.stamp.media.GifDecoder
import com.tjo7.stamp.media.GifWriter
import com.tjo7.stamp.media.Quantizer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Random

class GifCodecTest {
    private val colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFF00.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(), 0xFF00FFFF.toInt())

    private fun randomFrame(w: Int, h: Int, seed: Long): IntArray {
        val r = Random(seed); return IntArray(w * h) { colors[r.nextInt(colors.size)] }
    }

    @Test fun lzwRoundTripLargeRandom() {
        val r = Random(1)
        for (minCode in intArrayOf(2, 4, 8)) {
            val n = 120_000
            val data = ByteArray(n) { r.nextInt(1 shl minCode).toByte() }
            val enc = GifWriter.lzwEncode(data, minCode)
            val dec = GifDecoder.lzwDecode(minCode, enc, n)
            assertArrayEquals("minCode=$minCode", data, dec)
        }
    }

    @Test fun lzwRoundTripRepetitive() {
        val data = ByteArray(50_000) { (it / 700 % 4).toByte() }
        assertArrayEquals(data, GifDecoder.lzwDecode(2, GifWriter.lzwEncode(data, 2), data.size))
    }

    @Test fun fullFramesRoundTrip() {
        val w = 40; val h = 30
        val frames = (0 until 4).map { randomFrame(w, h, it.toLong()) }
        val q = Quantizer.build(frames, true)
        val bo = ByteArrayOutputStream()
        val gw = GifWriter(bo, w, h, q.palette, q.transparentIndex)
        for (f in frames) { val idx = ByteArray(w * h); q.map(f, idx); gw.writeFrame(idx, 120) }
        gw.finish()
        val bytes = bo.toByteArray()
        val hd = GifDecoder.readHeader(bytes)
        assertEquals(w, hd.width); assertEquals(h, hd.height)
        val sc = GifDecoder.scan(bytes)
        assertEquals(4, sc.frames); assertEquals(480, sc.totalMs)
        var n = 0
        GifDecoder.decode(bytes) { px, d, i -> assertEquals(120, d); assertArrayEquals("frame $i", frames[i], px); n++; true }
        assertEquals(4, n)
    }

    @Test fun deltaFrameKeepsPreviousPixels() {
        val w = 8; val h = 8
        val a = IntArray(w * h) { colors[0] }
        val q = Quantizer.build(listOf(a, IntArray(w * h) { colors[1] }), true)
        val bo = ByteArrayOutputStream()
        val gw = GifWriter(bo, w, h, q.palette, q.transparentIndex)
        val i0 = ByteArray(w * h); q.map(a, i0); gw.writeFrame(i0, 100)
        // second frame: only a 2x2 rectangle at (3,3) changes, everything else is transparent
        val sub = ByteArray(4) { q.indexOf(colors[1]).toByte() }
        gw.writeFrame(sub, 100, 3, 3, 2, 2, 1)
        gw.finish()
        val out = ArrayList<IntArray>()
        GifDecoder.decode(bo.toByteArray()) { px, _, _ -> out.add(px.copyOf()); true }
        assertEquals(2, out.size)
        assertEquals(colors[1], out[1][3 * w + 3]); assertEquals(colors[0], out[1][0])
    }

    @Test fun transparentIndexStaysTransparent() {
        val w = 4; val h = 4
        val f = IntArray(w * h) { if (it % 2 == 0) 0 else colors[5] }
        val q = Quantizer.build(listOf(f), true)
        assertEquals(0, q.indexOf(0))
        val bo = ByteArrayOutputStream()
        val gw = GifWriter(bo, w, h, q.palette, q.transparentIndex)
        val idx = ByteArray(w * h); q.map(f, idx); gw.writeFrame(idx, 100, disposal = 2); gw.finish()
        GifDecoder.decode(bo.toByteArray()) { px, _, _ -> assertEquals(0, px[0]); assertEquals(colors[5], px[1]); true }
    }

    @Test fun quantizerLimitsPaletteTo256() {
        val r = Random(5)
        val f = IntArray(200 * 200) { 0xFF000000.toInt() or r.nextInt(0xFFFFFF) }
        val q = Quantizer.build(listOf(f), true)
        assertTrue(q.palette.size <= 256)
        val idx = ByteArray(f.size); q.map(f, idx)
        assertTrue(idx.all { (it.toInt() and 255) < q.palette.size })
    }

    @Test fun rejectsGarbage() {
        try { GifDecoder.readHeader(ByteArray(20)); fail() } catch (e: GifDecoder.GifException) { }
    }
}
