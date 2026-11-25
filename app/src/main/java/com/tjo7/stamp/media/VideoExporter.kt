@file:OptIn(UnstableApi::class)

package com.tjo7.stamp.media

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import com.tjo7.stamp.domain.CreditMode
import com.tjo7.stamp.domain.StampSettings
import com.tjo7.stamp.domain.creditVisibleAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Hardware-accelerated H.264/AAC export with the credit composited by Media3 (audio is kept unless asked otherwise). */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoExporter(private val ctx: Context, private val renderer: StampRenderer) {

    @androidx.annotation.OptIn(UnstableApi::class)
    private class CreditOverlay(
        private val shown: Bitmap, private val hidden: Bitmap, private val mode: CreditMode,
    ) : BitmapOverlay() {
        override fun getBitmap(presentationTimeUs: Long): Bitmap =
            if (creditVisibleAt(mode, presentationTimeUs / 1_000_000.0)) shown else hidden
    }

    suspend fun export(info: SourceInfo, s: StampSettings, w: Int, h: Int, outFile: File, onProgress: (Float) -> Unit) {
        val shown = renderer.creditLayer(w, h, s)
        val hidden = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val overlay = CreditOverlay(shown, hidden, s.mode)
        val effects = ArrayList<androidx.media3.common.Effect>()
        effects.add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
        if (s.text.isNotBlank()) effects.add(OverlayEffect(ImmutableList.of(overlay)))
        val item = EditedMediaItem.Builder(MediaItem.fromUri(info.uri))
            .setRemoveAudio(!s.keepAudio || !info.hasAudio)
            .setEffects(Effects(emptyList(), effects))
            .build()
        // Keep the visual quality of the source: target its bitrate, scaled by the pixel-count change.
        val srcBr = if (info.bitrate > 0) info.bitrate.toDouble() else 8_000_000.0
        val ratio = (w.toDouble() * h) / (info.width.toDouble() * info.height)
        val br = (srcBr * minOf(1.0, ratio).coerceAtLeast(0.25)).toInt().coerceIn(1_500_000, 50_000_000)
        val encoderFactory = DefaultEncoderFactory.Builder(ctx)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(br).build())
            .build()
        outFile.delete()
        try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val listener = object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(Unit) }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resumeWithException(com.tjo7.stamp.media.ExportException("video: " + exportException.errorCodeName, exportException))
                        }
                    }
                    val transformer = Transformer.Builder(ctx)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoderFactory)
                        .addListener(listener)
                        .build()
                    val handler = Handler(Looper.getMainLooper())
                    val holder = ProgressHolder()
                    val poll = object : Runnable {
                        override fun run() {
                            if (!cont.isActive) return
                            if (transformer.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) onProgress(holder.progress / 100f)
                            handler.postDelayed(this, 300)
                        }
                    }
                    cont.invokeOnCancellation { handler.post { try { transformer.cancel() } catch (_: Exception) { } }; handler.removeCallbacks(poll) }
                    try { transformer.start(item, outFile.absolutePath) } catch (e: Exception) { cont.resumeWithException(com.tjo7.stamp.media.ExportException("video start", e)); return@suspendCancellableCoroutine }
                    handler.post(poll)
                }
            }
        } catch (e: Exception) {
            outFile.delete(); throw e
        } finally {
            shown.recycle(); hidden.recycle()
        }
        if (!outFile.exists() || outFile.length() < 1024) throw com.tjo7.stamp.media.ExportException("video empty")
    }
}
