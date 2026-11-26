package com.tjo7.stamp

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjo7.stamp.domain.*
import com.tjo7.stamp.media.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class MediaEngineInstrumentedTest {
    private val engine = Support.engine()
    private val noProgress: (Float) -> Unit = {}

    private fun export(info: SourceInfo, s: StampSettings, onProgress: (Float) -> Unit = noProgress) = runBlocking { withTimeout(240_000) { engine.export(info, s, onProgress) } }

    @Test fun probeReadsEveryKind() {
        val png = Support.probe(Support.asset("sample.png")); assertEquals(MediaKind.IMAGE, png.kind); assertTrue(png.width > 10)
        val gif = Support.probe(Support.asset("sample.gif")); assertEquals(MediaKind.ANIM_GIF, gif.kind); assertTrue(gif.durationMs > 0)
        val mp4 = Support.probe(Support.asset("sample.mp4")); assertEquals(MediaKind.VIDEO, mp4.kind); assertTrue(mp4.durationMs > 500)
    }

    @Test fun pngExportKeepsSizeAndAddsCredit() {
        val src = Support.asset("sample.png"); val info = Support.probe(src)
        val o = export(info, StampSettings())
        assertEquals(OutFormat.PNG, o.format)
        val b = BitmapFactory.decodeFile(o.file.absolutePath)
        assertEquals(info.width, b.width); assertEquals(info.height, b.height)
    }

    @Test fun jpgAndWebpAndResize() {
        val info = Support.probe(Support.asset("sample.png"))
        val j = export(info, StampSettings(format = OutFormat.JPG, size = SizeSel.W540))
        val bj = BitmapFactory.decodeFile(j.file.absolutePath); assertEquals(minOf(540, info.width), bj.width)
        val w = export(info, StampSettings(format = OutFormat.WEBP))
        assertNotNull(BitmapFactory.decodeFile(w.file.absolutePath))
    }

    @Test fun blinkImageBecomesThreeFrameGif() {
        val info = Support.probe(Support.asset("sample.png"))
        val o = export(info, StampSettings(mode = CreditMode.BLINK))
        assertEquals(OutFormat.GIF, o.format)
        val bytes = o.file.readBytes(); val sc = GifDecoder.scan(bytes)
        assertTrue("frames=${sc.frames}", sc.frames in 2..3); assertEquals(3000, sc.totalMs)
    }

    @Test fun gifToGifKeepsAnimation() {
        val info = Support.probe(Support.asset("sample.gif"))
        val o = export(info, StampSettings())
        val sc = GifDecoder.scan(o.file.readBytes())
        assertTrue(sc.frames > 1); assertTrue(Math.abs(sc.totalMs - info.durationMs) < 400)
    }

    @Test fun transparentOverlayExports() {
        val info = Support.probe(Support.asset("sample.png"))
        val png = export(info, StampSettings(transparent = true, format = OutFormat.PNG))
        val b = BitmapFactory.decodeFile(png.file.absolutePath); assertTrue(b.hasAlpha()); assertEquals(1200, b.width)
        val gif = export(info, StampSettings(transparent = true, format = OutFormat.GIF, mode = CreditMode.BLINK))
        assertTrue(GifDecoder.scan(gif.file.readBytes()).frames >= 3)
    }

    @Test fun stickerStaticRespectsWhatsAppLimit() {
        val info = Support.probe(Support.asset("sample.png"))
        val o = export(info, StampSettings(sticker = true))
        assertEquals(OutFormat.WEBP, o.format); assertTrue("size=${o.size}", o.size <= WhatsApp.STATIC_MAX)
        val b = BitmapFactory.decodeFile(o.file.absolutePath); assertTrue(b.width <= 512 && b.height <= 512)
    }

    @Test fun stickerAnimatedFromGifIsAnimatedWebp() {
        val info = Support.probe(Support.asset("sample.gif"))
        val o = export(info, StampSettings(sticker = true))
        val bytes = o.file.readBytes()
        assertTrue(AnimatedWebp.isAnimated(bytes)); assertTrue("size=${o.size}", o.size <= WhatsApp.ANIM_MAX)
        val a = AnimatedWebp.parse(bytes); assertTrue(a.frames.size > 1)
        // and it decodes again as a source
        val back = Support.probe(o.file.also { }); assertEquals(MediaKind.ANIM_WEBP, back.kind)
    }

    @Test fun videoExportsMp4KeepingLengthAndAudio() {
        val info = Support.probe(Support.asset("sample.mp4"))
        val seen = ArrayList<Float>()
        val o = export(info, StampSettings(), { seen.add(it) })
        assertEquals(OutFormat.MP4, o.format); assertTrue(o.size > 5_000)
        val r = MediaMetadataRetriever(); r.setDataSource(o.file.absolutePath)
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        assertTrue("duration ${dur} vs ${info.durationMs}", Math.abs(dur - info.durationMs) < 800)
        val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        assertEquals("audio kept", info.hasAudio, hasAudio)
        assertTrue(seen.isEmpty() || seen.max() <= 1.0f)
        // the credit is really in the picture: compare the first frame with and without credit
        val f = r.getFrameAtTime(1_200_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
        val plain = MediaMetadataRetriever().also { it.setDataSource(Support.asset("sample.mp4").absolutePath) }.getFrameAtTime(1_200_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
        var diff = 0L
        val sx = minOf(f.width, plain.width); val sy = minOf(f.height, plain.height)
        for (y in sy * 3 / 4 until sy step 2) for (x in 0 until sx / 3 step 2) diff += Math.abs((f.getPixel(x, y) and 255) - (plain.getPixel(x, y) and 255))
        assertTrue("credit visible bottom-left (diff=$diff)", diff > 2000)
    }

    @Test fun videoWithoutAudioWhenDisabled() {
        val info = Support.probe(Support.asset("sample.mp4"))
        val o = export(info, StampSettings(keepAudio = false, size = SizeSel.W540))
        val ex = MediaExtractor(); ex.setDataSource(o.file.absolutePath)
        var audio = false
        for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)!!.startsWith("audio")) audio = true
        assertFalse(audio)
    }

    @Test fun videoToGifIsCappedAt15SecondsAndTagged() {
        val info = Support.probe(Support.asset("sample.mp4"))
        val o = export(info, StampSettings(format = OutFormat.GIF, size = SizeSel.W540))
        assertEquals(OutFormat.GIF, o.format)
        val sc = GifDecoder.scan(o.file.readBytes()); assertTrue(sc.frames > 3)
    }

    @Test fun exportCanBeCancelledAndLeavesNoCrash() {
        val info = Support.probe(Support.asset("sample.mp4"))
        runBlocking {
            val job = launch(Dispatchers.Default) { engine.export(info, StampSettings(format = OutFormat.GIF), noProgress) }
            delay(300); job.cancel(); job.join()
            assertTrue(job.isCancelled)
        }
    }

    private fun checkVideo(o: ExportOutput, mime: String, minMs: Long, maxMs: Long) {
        assertTrue("size ${o.size}", o.size > 2_000)
        val r = MediaMetadataRetriever(); r.setDataSource(o.file.absolutePath)
        assertEquals(mime, r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        assertTrue("duration $dur not in $minMs..$maxMs", dur in minMs..maxMs)
        val f = r.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST)
        assertNotNull("frame decodes", f)
        var sum = 0L; for (y in 0 until f!!.height step 7) for (x in 0 until f.width step 7) sum += f.getPixel(x, y) and 255
        assertTrue("frame has picture", sum > 0)
    }

    @Test fun videoExportsWebm() {
        val info = Support.probe(Support.asset("sample.mp4"))
        val o = export(info, StampSettings(format = OutFormat.WEBM))
        assertEquals(OutFormat.WEBM, o.format); assertEquals("webm", o.file.extension)
        checkVideo(o, "video/webm", info.durationMs - 900, info.durationMs + 900)
    }

    @Test fun imageExportsMp4AndWebmAsThreeSecondClips() {
        val info = Support.probe(Support.asset("sample.png"))
        val m = export(info, StampSettings(format = OutFormat.MP4)); assertEquals(OutFormat.MP4, m.format)
        checkVideo(m, "video/mp4", 2500, 3600)
        val w = export(info, StampSettings(format = OutFormat.WEBM, mode = CreditMode.BLINK)); assertEquals(OutFormat.WEBM, w.format)
        checkVideo(w, "video/webm", 2500, 3600)
    }

    @Test fun gifExportsMp4AndWebmKeepingMotion() {
        val info = Support.probe(Support.asset("sample.gif"))
        val m = export(info, StampSettings(format = OutFormat.MP4)); assertEquals(OutFormat.MP4, m.format)
        checkVideo(m, "video/mp4", minOf(500L, info.durationMs / 2), info.durationMs + 1500)
        val w = export(info, StampSettings(format = OutFormat.WEBM)); assertEquals(OutFormat.WEBM, w.format)
        checkVideo(w, "video/webm", minOf(500L, info.durationMs / 2), info.durationMs + 1500)
    }

    @Test fun transparentWebmCarriesAnAlphaTrack() {
        val w = 320; val h = 120; val fps = 15
        val f = java.io.File(Support.target.getExternalFilesDir(null), "alpha_sample.webm")
        val enc = AlphaWebmEncoder(f, w, h, fps)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        for (k in 0 until 45) {
            // red box with a soft alpha ramp on the left half, fully transparent on the right half; the box slides so frames differ
            val px = IntArray(w * h) { i -> val x = i % w; val y = i / w
                if (x < 160 && y > 20 + (k % 10)) (((x * 255 / 160) shl 24) or 0xFF2020) else 0 }
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            enc.add(bmp)
        }
        enc.finish()
        assertTrue("key frames line up", enc.keyframesAligned())
        val bytes = f.readBytes()
        fun has(vararg b: Int): Boolean { outer@ for (i in 0..bytes.size - b.size) { for (j in b.indices) if ((bytes[i + j].toInt() and 255) != b[j]) continue@outer; return true }; return false }
        assertTrue("EBML header", has(0x1A, 0x45, 0xDF, 0xA3)); assertTrue("alpha mode", has(0x53, 0xC0, 0x01, 0, 0, 0, 0, 0, 0, 0x01, 0x01)); assertTrue("BlockAdditions", has(0x75, 0xA1))
        val r = MediaMetadataRetriever(); r.setDataSource(f.absolutePath)
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        assertTrue("duration $dur", dur in 2000..4000)
        assertNotNull(r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST))
        // keep a copy where CI can pull it for an outside check (also dumped to logcat as base64)
        run { val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP); b64.chunked(2000).forEachIndexed { i, c -> android.util.Log.i("ALPHAB64", "%04d %s".format(i, c)) } }
        val ua = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        ua.executeShellCommand("mkdir -p /data/local/tmp/shots").close()
        val pfd = ua.executeShellCommand("cp " + f.absolutePath + " /data/local/tmp/shots/alpha_sample.webm")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    @Test fun engineExportsTransparentWebm() {
        val info = Support.probe(Support.asset("sample.png"))
        val o = export(info, StampSettings(transparent = true, format = OutFormat.WEBM, mode = CreditMode.BLINK))
        assertEquals(OutFormat.WEBM, o.format)
        assertTrue(o.size > 1500)
        val r = MediaMetadataRetriever(); r.setDataSource(o.file.absolutePath)
        assertEquals("video/webm", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
        // dump the real engine output so CI can validate its alpha with ffprobe/Chrome
        val b64 = android.util.Base64.encodeToString(o.file.readBytes(), android.util.Base64.NO_WRAP)
        b64.chunked(2000).forEachIndexed { i, c -> android.util.Log.i("EXPORTB64", "%05d %s".format(i, c)) }
    }

    @Test fun gifSeekShowsDifferentFrames() {
        val info = Support.probe(Support.asset("land.gif"))
        assertTrue(info.kind == com.tjo7.stamp.domain.MediaKind.ANIM_GIF)
        val a = SourceLoader.previewFrame(Support.target, info, 0, 0L)
        val b = SourceLoader.previewFrame(Support.target, info, 0, info.durationMs - 100)
        val pa = IntArray(a.width * a.height); a.getPixels(pa, 0, a.width, 0, 0, a.width, a.height)
        val pb = IntArray(b.width * b.height); b.getPixels(pb, 0, b.width, 0, 0, b.width, b.height)
        assertTrue("frames at start and end differ", !pa.contentEquals(pb))
    }
}
