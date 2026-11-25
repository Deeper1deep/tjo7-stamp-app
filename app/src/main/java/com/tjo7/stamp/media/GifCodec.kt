package com.tjo7.stamp.media

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Streaming GIF decoder: every composited frame is handed to the callback, nothing is kept in memory. */
object GifDecoder {
    class Header(val width: Int, val height: Int)

    class GifException(msg: String) : Exception(msg)

    class Scan(val frames: Int, val totalMs: Int)

    /** Counts frames and total duration without decoding pixel data. */
    fun scan(b: ByteArray): Scan {
        readHeader(b)
        var p = 13
        val flags = b[10].toInt() and 0xFF
        if (flags and 0x80 != 0) p += (2 shl (flags and 7)) * 3
        var frames = 0; var total = 0; var delay = 100
        while (p < b.size) {
            when (b[p++].toInt() and 0xFF) {
                0x3B -> return Scan(frames, total)
                0x21 -> {
                    val label = b[p++].toInt() and 0xFF
                    if (label == 0xF9 && p + 3 < b.size) { val d = u16(b, p + 2); delay = if (d <= 1) 100 else d * 10 }
                    p = skipBlocks(b, p)
                }
                0x2C -> {
                    val lf = b[p + 8].toInt() and 0xFF
                    p += 9
                    if (lf and 0x80 != 0) p += (2 shl (lf and 7)) * 3
                    p++
                    p = skipBlocks(b, p)
                    frames++; total += delay; delay = 100
                }
                else -> throw GifException("bad block")
            }
        }
        return Scan(frames, total)
    }

    fun readHeader(b: ByteArray): Header {
        if (b.size < 13 || b[0] != 'G'.code.toByte() || b[1] != 'I'.code.toByte() || b[2] != 'F'.code.toByte())
            throw GifException("not a gif")
        return Header(u16(b, 6), u16(b, 8))
    }

    /**
     * Decode all frames. [onFrame] receives the full composited ARGB canvas (reused buffer — copy if you keep it),
     * the frame delay in milliseconds and its index; return false to stop early.
     */
    fun decode(b: ByteArray, onFrame: (pixels: IntArray, delayMs: Int, index: Int) -> Boolean) {
        val hd = readHeader(b)
        val w = hd.width
        val h = hd.height
        if (w <= 0 || h <= 0 || w.toLong() * h > 64_000_000L) throw GifException("bad size")
        var p = 13
        var gct: IntArray? = null
        val flags = b[10].toInt() and 0xFF
        if (flags and 0x80 != 0) {
            val n = 2 shl (flags and 7)
            gct = readPalette(b, p, n); p += n * 3
        }
        val canvas = IntArray(w * h)
        var prevCanvas: IntArray? = null
        var disposal = 0        // disposal method of the frame being read (from its graphic control block)
        var lastDisposal = 0    // disposal method of the previously drawn frame, applied before drawing the next
        var delayMs = 100
        var transIdx = -1
        var index = 0
        var lastRect = intArrayOf(0, 0, 0, 0)
        while (p < b.size) {
            val code = b[p++].toInt() and 0xFF
            when (code) {
                0x3B -> return
                0x21 -> {
                    val label = b[p++].toInt() and 0xFF
                    if (label == 0xF9 && p + 5 < b.size) {
                        val f = b[p + 1].toInt() and 0xFF
                        disposal = (f shr 2) and 7
                        transIdx = if (f and 1 != 0) b[p + 4].toInt() and 0xFF else -1
                        val d = u16(b, p + 2)
                        delayMs = if (d <= 1) 100 else d * 10
                    }
                    p = skipBlocks(b, p)
                }
                0x2C -> {
                    val fx = u16(b, p); val fy = u16(b, p + 2); val fw = u16(b, p + 4); val fh = u16(b, p + 6)
                    val lf = b[p + 8].toInt() and 0xFF
                    p += 9
                    var pal = gct
                    if (lf and 0x80 != 0) { val n = 2 shl (lf and 7); pal = readPalette(b, p, n); p += n * 3 }
                    val interlaced = lf and 0x40 != 0
                    val minCode = b[p++].toInt() and 0xFF
                    val data = ByteArrayOutputStream()
                    while (p < b.size) {
                        val n = b[p++].toInt() and 0xFF
                        if (n == 0) break
                        data.write(b, p, minOf(n, b.size - p)); p += n
                    }
                    // dispose the previous frame first
                    when (lastDisposal) {
                        2 -> fillRect(canvas, w, h, lastRect, 0)
                        3 -> prevCanvas?.let { System.arraycopy(it, 0, canvas, 0, canvas.size) }
                    }
                    if (disposal == 3) {
                        if (prevCanvas == null) prevCanvas = IntArray(canvas.size)
                        System.arraycopy(canvas, 0, prevCanvas, 0, canvas.size)
                    }
                    val npix = fw.toLong() * fh
                    if (npix > 64_000_000L) throw GifException("bad frame")
                    val idx = lzwDecode(minCode, data.toByteArray(), npix.toInt())
                    val rows = rowOrder(fh, interlaced)
                    val palette = pal ?: IntArray(256) { 0xFF000000.toInt() }
                    for (r in 0 until fh) {
                        val y = fy + rows[r]
                        if (y >= h) continue
                        for (x in 0 until fw) {
                            val ix = idx[r * fw + x].toInt() and 0xFF
                            if (ix == transIdx) continue
                            val cx = fx + x
                            if (cx >= w) continue
                            canvas[y * w + cx] = if (ix < palette.size) palette[ix] else 0
                        }
                    }
                    lastRect = intArrayOf(fx, fy, fw, fh)
                    if (!onFrame(canvas, delayMs, index)) return
                    index++
                    lastDisposal = disposal
                    disposal = 0; transIdx = -1
                }
                else -> throw GifException("bad block")
            }
        }
    }

    private fun fillRect(c: IntArray, w: Int, h: Int, r: IntArray, v: Int) {
        for (y in r[1] until minOf(h, r[1] + r[3])) for (x in r[0] until minOf(w, r[0] + r[2])) c[y * w + x] = v
    }

    private fun rowOrder(h: Int, interlaced: Boolean): IntArray {
        if (!interlaced) return IntArray(h) { it }
        val out = IntArray(h); var k = 0
        for ((start, step) in listOf(0 to 8, 4 to 8, 2 to 4, 1 to 2)) { var y = start; while (y < h) { out[k++] = y; y += step } }
        return out
    }

    private fun skipBlocks(b: ByteArray, start: Int): Int {
        var p = start
        while (p < b.size) { val n = b[p++].toInt() and 0xFF; if (n == 0) break; p += n }
        return p
    }

    private fun readPalette(b: ByteArray, p: Int, n: Int) = IntArray(n) {
        val o = p + it * 3
        0xFF000000.toInt() or ((b[o].toInt() and 0xFF) shl 16) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() and 0xFF)
    }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    fun lzwDecode(minCode: Int, data: ByteArray, npix: Int): ByteArray {
        val out = ByteArray(npix)
        val clear = 1 shl minCode
        val eoi = clear + 1
        var codeSize = minCode + 1
        var next = eoi + 1
        val prefix = IntArray(4096)
        val suffix = ByteArray(4096)
        val stack = ByteArray(4097)
        for (i in 0 until clear) suffix[i] = i.toByte()
        var bits = 0; var nbits = 0; var pos = 0; var op = 0
        var old = -1; var first = 0
        while (op < npix) {
            while (nbits < codeSize && pos < data.size) { bits = bits or ((data[pos++].toInt() and 0xFF) shl nbits); nbits += 8 }
            if (nbits < codeSize) break
            val code = bits and ((1 shl codeSize) - 1)
            bits = bits ushr codeSize; nbits -= codeSize
            if (code == clear) { codeSize = minCode + 1; next = eoi + 1; old = -1; continue }
            if (code == eoi) break
            if (old == -1) { out[op++] = suffix[code]; old = code; first = code and 0xFF; continue }
            var cur = code
            var sp = 0
            if (code >= next) { stack[sp++] = first.toByte(); cur = old }
            while (cur >= clear) { if (sp >= 4096) throw GifException("corrupt"); stack[sp++] = suffix[cur]; cur = prefix[cur] }
            first = suffix[cur].toInt() and 0xFF
            stack[sp++] = first.toByte()
            while (sp > 0 && op < npix) out[op++] = stack[--sp]
            if (next < 4096) {
                prefix[next] = old; suffix[next] = first.toByte(); next++
                if (next == (1 shl codeSize) && codeSize < 12) codeSize++
            }
            old = code
        }
        return out
    }
}

/** Streams a GIF89a to [out]. Frames are palette indices; index [transparentIndex] is see-through when >= 0. */
class GifWriter(
    private val out: OutputStream, private val w: Int, private val h: Int,
    palette: IntArray, private val transparentIndex: Int = -1, loop: Boolean = true,
) {
    private val bitsPerPixel: Int
    private val palSize: Int

    init {
        var bpp = 1
        while ((1 shl bpp) < palette.size && bpp < 8) bpp++
        bitsPerPixel = bpp
        palSize = 1 shl bpp
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        w16(w); w16(h)
        out.write(0x80 or ((bpp - 1) shl 4) or (bpp - 1)); out.write(0); out.write(0)
        for (i in 0 until palSize) {
            val c = if (i < palette.size) palette[i] else 0
            out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF)
        }
        if (loop) {
            out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(3, 1, 0, 0, 0))
        }
    }

    private fun w16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    /** [disposal]: 1 = keep (delta frames), 2 = clear to transparent before the next frame (overlay output). */
    fun writeFrame(
        indices: ByteArray, delayMs: Int, fx: Int = 0, fy: Int = 0, fw: Int = w, fh: Int = h, disposal: Int = 1,
    ) {
        val cs = maxOf(2, Math.round(delayMs / 10.0).toInt())
        out.write(0x21); out.write(0xF9); out.write(4)
        val hasT = transparentIndex >= 0
        out.write((disposal shl 2) or (if (hasT) 1 else 0))
        w16(cs); out.write(if (hasT) transparentIndex else 0); out.write(0)
        out.write(0x2C); w16(fx); w16(fy); w16(fw); w16(fh); out.write(0)
        val minCode = maxOf(2, bitsPerPixel)
        out.write(minCode)
        val data = lzwEncode(indices, minCode)
        var p = 0
        while (p < data.size) { val n = minOf(255, data.size - p); out.write(n); out.write(data, p, n); p += n }
        out.write(0)
    }

    fun finish() { out.write(0x3B); out.flush() }

    companion object {
        fun lzwEncode(px: ByteArray, minCode: Int): ByteArray {
            val bo = ByteArrayOutputStream(px.size / 2 + 16)
            val clear = 1 shl minCode
            val eoi = clear + 1
            var codeSize = minCode + 1
            var next = eoi + 1
            var acc = 0; var nacc = 0
            fun emit(code: Int) {
                acc = acc or (code shl nacc); nacc += codeSize
                while (nacc >= 8) { bo.write(acc and 0xFF); acc = acc ushr 8; nacc -= 8 }
            }
            val hsize = 9001
            val hk = IntArray(hsize) { -1 }
            val hv = IntArray(hsize)
            emit(clear)
            if (px.isEmpty()) { emit(eoi); if (nacc > 0) bo.write(acc and 0xFF); return bo.toByteArray() }
            var prefix = px[0].toInt() and 0xFF
            for (i in 1 until px.size) {
                val c = px[i].toInt() and 0xFF
                val key = (prefix shl 8) or c
                var hi = ((c shl 4) xor prefix) % hsize
                var found = -1
                while (hk[hi] != -1) { if (hk[hi] == key) { found = hv[hi]; break }; hi++; if (hi == hsize) hi = 0 }
                if (found >= 0) { prefix = found; continue }
                emit(prefix)
                if (next < 4096) {
                    hk[hi] = key; hv[hi] = next++
                    if (next - 1 == (1 shl codeSize) && codeSize < 12) codeSize++
                } else {
                    emit(clear); java.util.Arrays.fill(hk, -1); next = eoi + 1; codeSize = minCode + 1
                }
                prefix = c
            }
            emit(prefix); emit(eoi)
            if (nacc > 0) bo.write(acc and 0xFF)
            return bo.toByteArray()
        }
    }
}

/** Median-cut quantiser + nearest colour mapping with a 15-bit lookup cache. */
class Quantizer private constructor(val palette: IntArray, val transparentIndex: Int) {
    private val lut = ShortArray(32768) { -1 }

    fun indexOf(argb: Int): Int {
        if (transparentIndex >= 0 && (argb ushr 24) < 128) return transparentIndex
        val key = ((argb shr 19) and 31 shl 10) or ((argb shr 11) and 31 shl 5) or ((argb shr 3) and 31)
        val c = lut[key].toInt()
        if (c >= 0) return c
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        var best = 0; var bd = Int.MAX_VALUE
        val start = if (transparentIndex == 0) 1 else 0
        for (i in start until palette.size) {
            if (i == transparentIndex) continue
            val p = palette[i]
            val dr = r - ((p shr 16) and 0xFF); val dg = g - ((p shr 8) and 0xFF); val db = b - (p and 0xFF)
            val d = 2 * dr * dr + 4 * dg * dg + 3 * db * db
            if (d < bd) { bd = d; best = i }
        }
        lut[key] = best.toShort()
        return best
    }

    fun map(src: IntArray, dst: ByteArray) { for (i in src.indices) dst[i] = indexOf(src[i]).toByte() }

    companion object {
        /** [samples]: pixel buffers to learn the palette from. Index 0 is reserved when [withTransparency]. */
        fun build(samples: List<IntArray>, withTransparency: Boolean, maxColors: Int = 256): Quantizer {
            val budget = 400_000
            val total = samples.sumOf { it.size.toLong() }.coerceAtLeast(1)
            val step = maxOf(1, (total / budget).toInt())
            val hist = HashMap<Int, Int>()
            for (s in samples) {
                var i = 0
                while (i < s.size) {
                    val p = s[i]; i += step
                    if ((p ushr 24) < 128) continue
                    val k = p and 0xFFFFFF
                    hist[k] = (hist[k] ?: 0) + 1
                }
            }
            val n = if (withTransparency) maxColors - 1 else maxColors
            val colors = medianCut(hist, n)
            val pal = IntArray(if (withTransparency) colors.size + 1 else colors.size)
            if (withTransparency) { pal[0] = 0; for (i in colors.indices) pal[i + 1] = colors[i] }
            else for (i in colors.indices) pal[i] = colors[i]
            return Quantizer(if (pal.isEmpty()) intArrayOf(0) else pal, if (withTransparency) 0 else -1)
        }

        private class Box(val items: IntArray, val counts: IntArray) {
            var rmin = 255; var rmax = 0; var gmin = 255; var gmax = 0; var bmin = 255; var bmax = 0
            init {
                for (c in items) {
                    val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                    if (r < rmin) rmin = r; if (r > rmax) rmax = r
                    if (g < gmin) gmin = g; if (g > gmax) gmax = g
                    if (b < bmin) bmin = b; if (b > bmax) bmax = b
                }
            }
            val range get() = maxOf(rmax - rmin, gmax - gmin, bmax - bmin)
            fun average(): Int {
                var r = 0L; var g = 0L; var b = 0L; var t = 0L
                for (i in items.indices) {
                    val c = items[i]; val n = counts[i].toLong()
                    r += ((c shr 16) and 0xFF) * n; g += ((c shr 8) and 0xFF) * n; b += (c and 0xFF) * n; t += n
                }
                if (t == 0L) t = 1
                return 0xFF000000.toInt() or ((r / t).toInt() shl 16) or ((g / t).toInt() shl 8) or (b / t).toInt()
            }
        }

        private fun medianCut(hist: Map<Int, Int>, n: Int): IntArray {
            if (hist.isEmpty()) return intArrayOf(0xFF000000.toInt())
            val keys = hist.keys.toIntArray()
            if (keys.size <= n) return IntArray(keys.size) { 0xFF000000.toInt() or keys[it] }
            val boxes = ArrayList<Box>()
            boxes.add(Box(keys, IntArray(keys.size) { hist[keys[it]]!! }))
            while (boxes.size < n) {
                var bi = -1; var br = 0
                for (i in boxes.indices) if (boxes[i].items.size > 1 && boxes[i].range > br) { br = boxes[i].range; bi = i }
                if (bi < 0) break
                val bx = boxes.removeAt(bi)
                val dr = bx.rmax - bx.rmin; val dg = bx.gmax - bx.gmin; val db = bx.bmax - bx.bmin
                val shift = if (dr >= dg && dr >= db) 16 else if (dg >= db) 8 else 0
                val order = bx.items.indices.sortedBy { (bx.items[it] shr shift) and 0xFF }
                var tot = 0L
                for (i in order) tot += bx.counts[i]
                var acc = 0L; var cut = 1
                for (k in order.indices) { acc += bx.counts[order[k]]; if (acc * 2 >= tot) { cut = k + 1; break } }
                cut = cut.coerceIn(1, order.size - 1)
                val a = order.subList(0, cut); val c = order.subList(cut, order.size)
                boxes.add(Box(IntArray(a.size) { bx.items[a[it]] }, IntArray(a.size) { bx.counts[a[it]] }))
                boxes.add(Box(IntArray(c.size) { bx.items[c[it]] }, IntArray(c.size) { bx.counts[c[it]] }))
            }
            return IntArray(boxes.size) { boxes[it].average() }
        }
    }
}
