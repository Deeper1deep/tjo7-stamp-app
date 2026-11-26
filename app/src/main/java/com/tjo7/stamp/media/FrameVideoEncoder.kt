package com.tjo7.stamp.media

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Encodes a stream of bitmaps into an MP4 (H.264) or WebM (VP9) file with MediaCodec + MediaMuxer.
 * Used for images and GIFs that are exported as video. Opaque only: no alpha channel.
 */
class FrameVideoEncoder(private val file: File, private val w: Int, private val h: Int, private val fps: Int, private val webm: Boolean) {
    private val mime = if (webm) MediaFormat.MIMETYPE_VIDEO_VP9 else MediaFormat.MIMETYPE_VIDEO_AVC
    private val codec: MediaCodec = MediaCodec.createEncoderByType(mime)
    private val muxer = MediaMuxer(file.absolutePath, if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var track = -1
    private var started = false
    private var n = 0
    private val info = MediaCodec.BufferInfo()
    private val px = IntArray(w * h)

    init {
        val f = MediaFormat.createVideoFormat(mime, w, h)
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        f.setInteger(MediaFormat.KEY_BIT_RATE, (w.toLong() * h * fps / 6).coerceIn(400_000L, 20_000_000L).toInt())
        f.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    /** Adds one frame (must be w x h). [repeat] writes the same picture several times. */
    fun add(b: Bitmap, times: Int = 1) {
        b.getPixels(px, 0, w, 0, 0, w, h)
        repeat(times) { feed(false) }
    }

    private fun feed(eos: Boolean) {
        while (true) {
            val i = codec.dequeueInputBuffer(10_000)
            if (i < 0) { drain(); continue }
            val pts = n * 1_000_000L / fps
            if (eos) { codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); break }
            val img = codec.getInputImage(i)!!
            fill(img)
            codec.queueInputBuffer(i, 0, w * h * 3 / 2, pts, 0)
            n++
            break
        }
        drain()
    }

    private fun fill(img: android.media.Image) {
        val y = img.planes[0]; val u = img.planes[1]; val v = img.planes[2]
        val yb = y.buffer; val ub = u.buffer; val vb = v.buffer
        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = px[j * w + i]
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
                val yy = ((66 * r + 129 * g + 25 * bl + 128) shr 8) + 16
                yb.put(j * y.rowStride + i * y.pixelStride, yy.coerceIn(16, 235).toByte())
                if (j % 2 == 0 && i % 2 == 0) {
                    val uu = ((-38 * r - 74 * g + 112 * bl + 128) shr 8) + 128
                    val vv = ((112 * r - 94 * g - 18 * bl + 128) shr 8) + 128
                    ub.put((j / 2) * u.rowStride + (i / 2) * u.pixelStride, uu.coerceIn(16, 240).toByte())
                    vb.put((j / 2) * v.rowStride + (i / 2) * v.pixelStride, vv.coerceIn(16, 240).toByte())
                }
            }
        }
    }

    private fun drain(): Boolean {
        var eos = false
        while (true) {
            val o = codec.dequeueOutputBuffer(info, 0)
            when {
                o == MediaCodec.INFO_TRY_AGAIN_LATER -> return eos
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true }
                o >= 0 -> {
                    val buf = codec.getOutputBuffer(o)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && started) { buf.position(info.offset); buf.limit(info.offset + info.size); muxer.writeSampleData(track, buf, info) }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { eos = true; return true }
                }
            }
        }
    }

    fun finish() {
        try {
            val i = codec.dequeueInputBuffer(2_000_000)
            if (i >= 0) codec.queueInputBuffer(i, 0, 0, n * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            var tries = 0
            while (tries++ < 400 && !drain()) Thread.sleep(10)
            if (started) muxer.stop()
        } finally {
            try { codec.stop() } catch (_: Exception) { }
            codec.release()
            try { muxer.release() } catch (_: Exception) { }
        }
    }

    fun abort() { try { codec.stop() } catch (_: Exception) { }; codec.release(); try { muxer.release() } catch (_: Exception) { } }
}
