package com.tjo7.stamp.media

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Pure-Kotlin WebP container helpers: mux animated WebP from per-frame stills and demux animated files. */
object AnimatedWebp {
    class WebpException(msg: String) : Exception(msg)

    /** One frame of an animated WebP. [chunks] are the image chunks (ALPH?, VP8/VP8L) as full chunk bytes. */
    class Frame(
        val x: Int, val y: Int, val w: Int, val h: Int, val durationMs: Int,
        val blend: Boolean, val disposeBg: Boolean, val chunks: ByteArray,
    )

    class Anim(val width: Int, val height: Int, val frames: List<Frame>)

    private fun u24(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16)
    private fun u32(b: ByteArray, o: Int) = u24(b, o) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun fourCC(b: ByteArray, o: Int) = String(b, o, 4, Charsets.US_ASCII)

    fun isWebp(b: ByteArray) = b.size >= 12 && fourCC(b, 0) == "RIFF" && fourCC(b, 8) == "WEBP"

    /** True when the file carries an ANIM chunk. */
    fun isAnimated(b: ByteArray): Boolean {
        if (!isWebp(b)) return false
        var p = 12
        while (p + 8 <= b.size) {
            val id = fourCC(b, p)
            val n = u32(b, p + 4)
            if (id == "ANIM") return true
            if (n < 0) return false
            p += 8 + n + (n and 1)
        }
        return false
    }

    /** Extract the raw image chunks (ALPH, VP8, VP8L) from a still WebP file, as complete chunks incl. headers. */
    fun imageChunks(b: ByteArray): ByteArray {
        if (!isWebp(b)) throw WebpException("not webp")
        val out = ByteArrayOutputStream()
        var p = 12
        while (p + 8 <= b.size) {
            val id = fourCC(b, p)
            val n = u32(b, p + 4)
            if (n < 0 || p + 8 + n > b.size) throw WebpException("truncated")
            if (id == "ALPH" || id == "VP8 " || id == "VP8L") {
                out.write(b, p, 8 + n); if (n and 1 == 1) out.write(0)
            }
            p += 8 + n + (n and 1)
        }
        if (out.size() == 0) throw WebpException("no image data")
        return out.toByteArray()
    }

    /** Wrap image chunks into a standalone still WebP (what BitmapFactory expects). */
    fun standalone(chunks: ByteArray, w: Int, h: Int, hasAlpha: Boolean): ByteArray {
        val o = ByteArrayOutputStream()
        val vp8x = ByteArray(18)
        vp8x[0] = 'V'.code.toByte(); vp8x[1] = 'P'.code.toByte(); vp8x[2] = '8'.code.toByte(); vp8x[3] = 'X'.code.toByte()
        vp8x[4] = 10
        vp8x[8] = if (hasAlpha) 0x10 else 0
        put24(vp8x, 12, w - 1); put24(vp8x, 15, h - 1)
        val body = vp8x.size + chunks.size + 4
        o.write("RIFF".toByteArray()); o.write(le32(body)); o.write("WEBP".toByteArray())
        o.write(vp8x); o.write(chunks)
        return o.toByteArray()
    }

    private fun put24(b: ByteArray, o: Int, v: Int) { b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte(); b[o + 2] = (v shr 16).toByte() }
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** Parse an animated WebP into frames. Throws if the file is not an animation. */
    fun parse(b: ByteArray): Anim {
        if (!isWebp(b)) throw WebpException("not webp")
        var p = 12
        var cw = 0; var ch = 0
        val frames = ArrayList<Frame>()
        while (p + 8 <= b.size) {
            val id = fourCC(b, p)
            val n = u32(b, p + 4)
            if (n < 0 || p + 8 + n > b.size) throw WebpException("truncated")
            when (id) {
                "VP8X" -> { cw = u24(b, p + 12) + 1; ch = u24(b, p + 15) + 1 }
                "ANMF" -> {
                    val d = p + 8
                    val fx = u24(b, d) * 2; val fy = u24(b, d + 3) * 2
                    val fw = u24(b, d + 6) + 1; val fh = u24(b, d + 9) + 1
                    val dur = u24(b, d + 12)
                    val fl = b[d + 15].toInt() and 0xFF
                    frames.add(Frame(fx, fy, fw, fh, dur, fl and 2 == 0, fl and 1 == 1, b.copyOfRange(d + 16, d + n)))
                }
            }
            p += 8 + n + (n and 1)
        }
        if (frames.isEmpty()) throw WebpException("no frames")
        return Anim(cw, ch, frames)
    }

    /** Streaming muxer: add per-frame still WebP files, then [finish] to get the animated container. */
    class Builder(private val w: Int, private val h: Int) {
        private class Piece(val chunks: ByteArray, val durationMs: Int)
        private val pieces = ArrayList<Piece>()
        var payloadBytes = 0L
            private set

        /** [still] is a complete still WebP (e.g. from Bitmap.compress). */
        fun addFrame(still: ByteArray, durationMs: Int) {
            val c = imageChunks(still)
            pieces.add(Piece(c, durationMs.coerceIn(8, 0xFFFFFF)))
            payloadBytes += c.size + 32
        }

        val frameCount get() = pieces.size

        fun writeTo(out: OutputStream) {
            val body = ByteArrayOutputStream()
            body.write("WEBP".toByteArray())
            writeChunk(body, "VP8X", ByteArray(10).also { it[0] = 0x12; put24(it, 4, w - 1); put24(it, 7, h - 1) })
            writeChunk(body, "ANIM", ByteArray(6))
            for (pc in pieces) {
                val hd = ByteArray(16)
                put24(hd, 6, w - 1); put24(hd, 9, h - 1); put24(hd, 12, pc.durationMs)
                hd[15] = 0x02 // do not blend, do not dispose
                val d = ByteArray(hd.size + pc.chunks.size)
                System.arraycopy(hd, 0, d, 0, hd.size); System.arraycopy(pc.chunks, 0, d, hd.size, pc.chunks.size)
                writeChunk(body, "ANMF", d)
            }
            out.write("RIFF".toByteArray()); out.write(le32(body.size())); out.write(body.toByteArray())
            out.flush()
        }

        private fun writeChunk(o: OutputStream, id: String, data: ByteArray) {
            o.write(id.toByteArray(Charsets.US_ASCII)); o.write(le32(data.size)); o.write(data)
            if (data.size and 1 == 1) o.write(0)
        }
    }
}
