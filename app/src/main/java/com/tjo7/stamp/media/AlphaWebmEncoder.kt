package com.tjo7.stamp.media

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Transparent WebM: VP8 with an alpha channel, the same layout Chrome's MediaRecorder and ffmpeg write.
 * The colour picture and the alpha plane (as a grey picture) are encoded by two MediaCodec VP8 encoders and a small
 * Matroska writer stores each alpha frame next to its colour frame as BlockAdditions (BlockAddID 1) with AlphaMode = 1.
 * No native code is bundled.
 */
class AlphaWebmEncoder(private val file: File, private val w: Int, private val h: Int, private val fps: Int) {
    private class Frame(val data: ByteArray, val key: Boolean)

    private inner class Enc(bitrate: Int) {
        val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_VP8)
        val out = ArrayList<Frame>()
        val info = MediaCodec.BufferInfo()
        var n = 0
        init {
            val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_VP8, w, h)
            f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            f.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            f.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 100000)
            codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        }
        fun feed(fill: (android.media.Image) -> Unit) {
            while (true) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i < 0) { drain(); continue }
                // Both encoders get a sync frame at exactly the same frame numbers so colour and alpha stay aligned.
                if (n % fps == 0 && n > 0) codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
                fill(codec.getInputImage(i)!!)
                codec.queueInputBuffer(i, 0, w * h * 3 / 2, n * 1_000_000L / fps, 0)
                n++
                break
            }
            drain()
        }
        fun drain(): Boolean {
            while (true) {
                val o = codec.dequeueOutputBuffer(info, 0)
                when {
                    o == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val b = ByteArray(info.size); buf.position(info.offset); buf.limit(info.offset + info.size); buf.get(b)
                            out.add(Frame(b, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0))
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                    }
                }
            }
        }
        fun finish() {
            val i = codec.dequeueInputBuffer(2_000_000)
            if (i >= 0) codec.queueInputBuffer(i, 0, 0, n * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            var tries = 0
            while (tries++ < 500 && !drain()) Thread.sleep(10)
        }
        fun release() { try { codec.stop() } catch (_: Exception) { }; try { codec.release() } catch (_: Exception) { } }
    }

    private val bitrate = (w.toLong() * h * fps / 5).coerceIn(300_000L, 12_000_000L).toInt()
    private val color = Enc(bitrate)
    private val alpha = Enc((bitrate / 3).coerceAtLeast(150_000))
    private val px = IntArray(w * h)

    fun add(b: Bitmap, times: Int = 1) {
        b.getPixels(px, 0, w, 0, 0, w, h)
        repeat(times) {
            color.feed { img -> fillColor(img) }
            alpha.feed { img -> fillAlpha(img) }
        }
    }

    private fun fillColor(img: android.media.Image) {
        val y = img.planes[0]; val u = img.planes[1]; val v = img.planes[2]
        for (j in 0 until h) for (i in 0 until w) {
            val c = px[j * w + i]
            // Straight (not premultiplied) colour. Fully transparent pixels get black.
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
            y.buffer.put(j * y.rowStride + i * y.pixelStride, (((66 * r + 129 * g + 25 * bl + 128) shr 8) + 16).coerceIn(16, 235).toByte())
            if (j % 2 == 0 && i % 2 == 0) {
                u.buffer.put((j / 2) * u.rowStride + (i / 2) * u.pixelStride, (((-38 * r - 74 * g + 112 * bl + 128) shr 8) + 128).coerceIn(16, 240).toByte())
                v.buffer.put((j / 2) * v.rowStride + (i / 2) * v.pixelStride, (((112 * r - 94 * g - 18 * bl + 128) shr 8) + 128).coerceIn(16, 240).toByte())
            }
        }
    }

    private fun fillAlpha(img: android.media.Image) {
        val y = img.planes[0]; val u = img.planes[1]; val v = img.planes[2]
        for (j in 0 until h) for (i in 0 until w) {
            y.buffer.put(j * y.rowStride + i * y.pixelStride, ((px[j * w + i] ushr 24) and 255).toByte())
            if (j % 2 == 0 && i % 2 == 0) {
                u.buffer.put((j / 2) * u.rowStride + (i / 2) * u.pixelStride, 128.toByte())
                v.buffer.put((j / 2) * v.rowStride + (i / 2) * v.pixelStride, 128.toByte())
            }
        }
    }

    fun finish() {
        try {
            color.finish(); alpha.finish()
            val n = minOf(color.out.size, alpha.out.size)
            require(n > 0) { "no frames encoded" }
            writeMatroska(n)
        } finally { color.release(); alpha.release() }
    }

    fun abort() { color.release(); alpha.release() }

    // ------------------------------------------------------------------ Matroska
    private fun vint(v: Long): ByteArray { // always 8 bytes: 0x01 + 7 bytes
        val b = ByteArray(8); b[0] = 0x01
        for (i in 1..7) b[i] = ((v shr (8 * (7 - i))) and 0xFF).toByte()
        return b
    }
    private fun idBytes(id: Long): ByteArray { var n = 1; while ((id shr (8 * n)) != 0L) n++; return ByteArray(n) { ((id shr (8 * (n - 1 - it))) and 0xFF).toByte() } }
    private fun el(id: Long, data: ByteArray): ByteArray { val o = ByteArrayOutputStream(); o.write(idBytes(id)); o.write(vint(data.size.toLong())); o.write(data); return o.toByteArray() }
    private fun uint(id: Long, v: Long): ByteArray { var n = 1; while (n < 8 && (v shr (8 * n)) != 0L) n++; return el(id, ByteArray(n) { ((v shr (8 * (n - 1 - it))) and 0xFF).toByte() }) }
    private fun str(id: Long, s: String) = el(id, s.toByteArray(Charsets.US_ASCII))
    private fun flt(id: Long, v: Double) = el(id, java.nio.ByteBuffer.allocate(8).putDouble(v).array())
    private fun cat(vararg parts: ByteArray): ByteArray { val o = ByteArrayOutputStream(); parts.forEach { o.write(it) }; return o.toByteArray() }

    private fun writeMatroska(n: Int) {
        val frameMs = 1000.0 / fps
        val header = el(0x1A45DFA3, cat(uint(0x4286, 1), uint(0x42F7, 1), uint(0x42F2, 4), uint(0x42F3, 8), str(0x4282, "webm"), uint(0x4287, 4), uint(0x4285, 2)))
        val info = el(0x1549A966, cat(uint(0x2AD7B1, 1_000_000), flt(0x4489, n * frameMs), str(0x4D80, "t.jo7"), str(0x5741, "t.jo7")))
        val video = el(0xE0, cat(uint(0xB0, w.toLong()), uint(0xBA, h.toLong()), uint(0x53C0, 1)))
        val track = el(0xAE, cat(uint(0xD7, 1), uint(0x73C5, 1), uint(0x83, 1), uint(0x9C, 0), str(0x86, "V_VP8"), video))
        val tracks = el(0x1654AE6B, track)
        val clusters = ByteArrayOutputStream()
        var i = 0
        while (i < n) {
            val start = i
            val clusterTime = Math.round(start * frameMs)
            val body = ByteArrayOutputStream()
            body.write(uint(0xE7, clusterTime))
            do {
                val ms = Math.round(i * frameMs)
                val rel = (ms - clusterTime).toInt()
                val cf = color.out[i]; val af = alpha.out[i]
                val block = cat(byteArrayOf(0x81.toByte(), ((rel shr 8) and 0xFF).toByte(), (rel and 0xFF).toByte(), 0x00), cf.data)
                val parts = ArrayList<ByteArray>()
                parts.add(el(0xA1, block))
                parts.add(uint(0x9B, Math.max(1L, Math.round((i + 1) * frameMs) - ms)))
                if (!cf.key) parts.add(el(0xFB, java.nio.ByteBuffer.allocate(2).putShort((-Math.round(frameMs).toInt()).toShort()).array()))
                parts.add(el(0x75A1, el(0xA6, cat(uint(0xEE, 1), el(0xA5, af.data)))))
                body.write(el(0xA0, cat(*parts.toTypedArray())))
                i++
            } while (i < n && !color.out[i].key)
            clusters.write(el(0x1F43B675, body.toByteArray()))
        }
        val segment = el(0x18538067, cat(info, tracks, clusters.toByteArray()))
        file.writeBytes(cat(header, segment))
    }

    /** Debug aid for tests: do colour and alpha frames agree on which frames are key frames? */
    fun keyframesAligned(): Boolean { val n = minOf(color.out.size, alpha.out.size); return (0 until n).all { color.out[it].key == alpha.out[it].key } }
}
