package com.tjo7.stamp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Saves one PNG per tab and theme so the new UI can be compared with the old one. Not an assertion test. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File(Support.target.getExternalFilesDir(null), "shots").apply { mkdirs() }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun captureAllTabs() {
        for (round in listOf("dark", "light")) {
            for (t in listOf("editor", "credit", "output", "settings")) {
                rule.onNodeWithTag("tab_$t").performClick()
                shot("new_${round}_$t")
            }
            rule.onNodeWithTag("theme_toggle").performClick()
        }
    }
}
