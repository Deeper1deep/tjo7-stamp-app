package com.tjo7.stamp

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.tjo7.stamp.media.MediaEngine
import com.tjo7.stamp.media.SourceInfo
import com.tjo7.stamp.media.SourceLoader
import com.tjo7.stamp.media.StampRenderer
import java.io.File

object Support {
    val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val testCtx: Context get() = InstrumentationRegistry.getInstrumentation().context

    fun renderer(): StampRenderer {
        val reg = androidx.core.content.res.ResourcesCompat.getFont(target, R.font.poppins_regular) ?: Typeface.DEFAULT
        val bold = androidx.core.content.res.ResourcesCompat.getFont(target, R.font.poppins_bold) ?: Typeface.DEFAULT_BOLD
        return StampRenderer(reg, bold)
    }

    fun engine() = MediaEngine(target, renderer())

    /** Copies a test asset into the target app cache and returns its file. */
    fun asset(name: String, as_: String = name): File {
        val f = File(target.cacheDir, "t-$as_")
        testCtx.assets.open(name).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return f
    }

    fun probe(f: File, id: Long = 1): SourceInfo = SourceLoader.probe(target, Uri.fromFile(f), id)
}
