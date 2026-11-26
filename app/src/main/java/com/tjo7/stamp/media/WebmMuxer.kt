@file:OptIn(UnstableApi::class)

package com.tjo7.stamp.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.Muxer
import com.google.common.collect.ImmutableList
import java.io.IOException
import java.nio.ByteBuffer

/** Media3 [Muxer] that writes WebM (VP8/VP9 + Opus/Vorbis) through the platform MediaMuxer. */
@androidx.annotation.OptIn(UnstableApi::class)
class WebmMuxer private constructor(private val mm: MediaMuxer) : Muxer {
    private class Token(val index: Int) : Muxer.TrackToken
    private var started = false
    private var tracks = 0
    private var expected = 1
    private var added = 0

    @androidx.annotation.OptIn(UnstableApi::class)
    class Factory(private val withAudio: Boolean) : Muxer.Factory {
        override fun create(path: String): Muxer = try {
            WebmMuxer(MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)).also { it.expected = if (withAudio) 2 else 1 }
        } catch (e: IOException) { throw Muxer.MuxerException("webm create", e) }

        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> = when (trackType) {
            C.TRACK_TYPE_VIDEO -> ImmutableList.of(MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_VP8)
            C.TRACK_TYPE_AUDIO -> ImmutableList.of(MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS)
            else -> ImmutableList.of()
        }
    }

    override fun addTrack(format: Format): Muxer.TrackToken {
        val mime = format.sampleMimeType ?: throw Muxer.MuxerException("no mime", IllegalStateException())
        val mf = if (MimeTypes.isVideo(mime)) MediaFormat.createVideoFormat(mime, format.width, format.height)
        else MediaFormat.createAudioFormat(mime, format.sampleRate, format.channelCount)
        MediaFormatUtil.setCsdBuffers(mf, format.initializationData)
        return try { Token(mm.addTrack(mf)).also { tracks++ } } catch (e: RuntimeException) { throw Muxer.MuxerException("webm addTrack", e) }
    }

    override fun writeSampleData(trackToken: Muxer.TrackToken, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!started) { mm.start(); started = true }
        try { mm.writeSampleData((trackToken as Token).index, byteBuffer, bufferInfo) }
        catch (e: RuntimeException) { throw IllegalStateException("webm write", e) }
    }

    override fun addMetadataEntry(metadataEntry: Metadata.Entry) {}

    override fun close() {
        try { if (started) mm.stop() } catch (e: RuntimeException) { throw Muxer.MuxerException("webm stop", e) } finally { mm.release() }
    }
}
