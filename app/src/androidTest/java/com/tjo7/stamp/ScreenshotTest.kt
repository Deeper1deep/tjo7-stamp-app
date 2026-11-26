package com.tjo7.stamp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private object ParcelReader {
    fun drain(fd: android.os.ParcelFileDescriptor) { android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
}

/** Saves one PNG per tab and theme so the new UI can be compared with the old one. Not an assertion test. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val vm get() = androidx.lifecycle.ViewModelProvider(rule.activity)[com.tjo7.stamp.ui.MainViewModel::class.java]

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(800)
        // The app (and its files) is uninstalled when the test task ends, so keep the screencap where CI can pull it.
        val ua = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        ua.executeShellCommand("mkdir -p /data/local/tmp/shots").close()
        ParcelReader.drain(ua.executeShellCommand("screencap -p /data/local/tmp/shots/$name.png"))
    }

    /** A live update check can open the update sheet over the app; photograph it once, then close it so taps reach the screen. */
    private fun clearSheet(tag: String) {
        if (vm.ui.value.updateSheet) { shot("sheet_upd_$tag"); rule.runOnUiThread { vm.openUpdateSheet(false) }; rule.mainClock.advanceTimeBy(800) }
    }

    private fun info() {
        val dm = Support.target.resources.displayMetrics
        val txt = "px=" + dm.widthPixels + "x" + dm.heightPixels + " density=" + dm.density + " dpi=" + dm.densityDpi + " fontScale=" + Support.target.resources.configuration.fontScale +
            " dp=" + (dm.widthPixels / dm.density) + "x" + (dm.heightPixels / dm.density)
        val dir = File(Support.target.cacheDir, "info.txt"); dir.writeText(txt)
        val ua = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        ua.executeShellCommand("mkdir -p /data/local/tmp/shots").close()
        ParcelReader.drain(ua.executeShellCommand("sh -c 'echo \"" + txt + "\" > /data/local/tmp/shots/info.txt'"))
    }

    /** Taps a tagged node, first closing the live update sheet if it is covering the app. */
    private fun tap(tag: String) { clearSheet("t" + tag); rule.onNodeWithTag(tag).performClick(); rule.mainClock.advanceTimeBy(400) }

    private fun isDark() = vm.ui.value.dark

    @Test fun captureAllTabs() {
        // The blink preview animates forever; freeze the test clock so it can be captured.
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1000)
        info()
        for (k in 0 until 8) { Thread.sleep(250); rule.mainClock.advanceTimeBy(100) }
        clearSheet("a")
        // Force dark first.
        if (!isDark()) tap("theme_toggle")
        rule.mainClock.advanceTimeBy(800)
        for (round in listOf("dark", "light")) {
            for (t in listOf("editor", "credit", "output", "settings")) {
                clearSheet("$round$t")
                tap("tab_$t")
                shot("new_${round}_$t")
            }
            tap("tab_editor")
            tap("theme_toggle")
            rule.mainClock.advanceTimeBy(800)
        }
    }

    /** Same screens with a video, an image and a GIF loaded, plus the results sheet. */
    @Test fun capturePopulated() {
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1000)
        for (k in 0 until 8) { Thread.sleep(250); rule.mainClock.advanceTimeBy(100) }
        clearSheet("b")
        if (!isDark()) tap("theme_toggle")
        rule.runOnUiThread { vm.edit { com.tjo7.stamp.domain.StampSettings() } }  // factory settings, same as the reference screenshots
        val files = listOf(Support.asset("land.mp4", "p.mp4"), Support.asset("land.png", "p.png"), Support.asset("sample.gif", "p.gif"))
        rule.runOnUiThread { vm.importUris(files.map { android.net.Uri.fromFile(it) }, false) }
        // Import and preview run on background dispatchers; give them real time and let the frozen clock tick between polls.
        for (k in 0 until 40) { Thread.sleep(250); rule.mainClock.advanceTimeBy(100) }
        for (t in listOf("editor", "credit", "output", "settings")) {
            clearSheet(t)
            tap("tab_$t")
            if (t == "credit") rule.runOnUiThread { vm.setMove(true) }
            shot("pop_dark_$t")
        }
        tap("tab_editor")
        tap("save_btn")
        for (k in 0 until 120) { Thread.sleep(500); rule.mainClock.advanceTimeBy(100); if (vm.ui.value.resultsSheet) break }
        for (k in 0 until 6) { Thread.sleep(300); rule.mainClock.advanceTimeBy(300) }
        shot("pop_dark_results")
    }
}
