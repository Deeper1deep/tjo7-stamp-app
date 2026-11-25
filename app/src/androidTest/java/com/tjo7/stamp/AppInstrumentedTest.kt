package com.tjo7.stamp

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjo7.stamp.data.*
import com.tjo7.stamp.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun tabsNavigateAndShowTheirScreens() {
        rule.onNodeWithTag("add_files").assertIsDisplayed()
        rule.onNodeWithTag("tab_credit").performClick()
        rule.onNodeWithTag("credit_text").assertIsDisplayed()
        rule.onNodeWithTag("tab_output").performClick()
        rule.onNodeWithTag("tab_settings").performClick()
        rule.onNodeWithTag("check_update").assertIsDisplayed()
        rule.onNodeWithTag("tab_editor").performClick()
        rule.onNodeWithTag("add_files").assertIsDisplayed()
    }

    @Test fun editingTheCreditTextWorks() {
        rule.onNodeWithTag("tab_credit").performClick()
        rule.onNodeWithTag("credit_text").performTextClearance()
        rule.onNodeWithTag("credit_text").performTextInput("@abc")
        rule.waitForIdle()
    }
}

@RunWith(AndroidJUnit4::class)
class DataInstrumentedTest {
    private val ctx get() = Support.target

    @Test fun settingsPersistAndMoveOffsetDoesNot() = runBlocking {
        val repo = SettingsRepository(ctx)
        repo.reset()
        val s = StampSettings(text = "@x", corner = Corner.TR, widthPct = 30, marginPct = 5, opacity = 80, shadow = 40, white = false, bold = true,
            mode = CreditMode.BLINK, format = OutFormat.GIF, size = SizeSel.W720, keepAudio = false, stickerCrop = false, dx = 12f, dy = -7f)
        repo.save(s)
        val back = repo.load()
        assertEquals(s.copy(dx = 0f, dy = 0f), back)
        repo.reset()
        assertEquals(StampSettings(), repo.load())
    }

    @Test fun importerReadsImagesAndStickerPacks() {
        val png = Support.asset("sample.png")
        val pack = File(ctx.cacheDir, "pack.zip")
        // build a pack with a webp (made by our own exporter) + a tray file
        val engine = Support.engine()
        val webp = runBlocking { engine.export(Support.probe(png, 7), StampSettings(sticker = true)) { } }
        pack.outputStream().use { ZipUtil.write(listOf("one.webp" to webp.file, "tray.png" to png), it) }
        val r = Importer(ctx).import(listOf(Uri.fromFile(png), Uri.fromFile(pack), Uri.fromFile(File("/nonexistent"))), copy = false)
        assertEquals(1 + 1, r.items.size); assertEquals(1, r.failed)
        assertNotNull(r.items[1].pack); assertEquals(1, r.items[1].pack!!.others.size)
    }

    @Test fun saverWritesIntoTheGallery() {
        val f = File(ctx.cacheDir, "saver-test.png"); Support.asset("sample.png").copyTo(f, true)
        val saved = MediaSaver(ctx).save(f, "tjo7-test-" + System.currentTimeMillis() + ".png", "image/png")
        assertNotNull(saved.uri); assertTrue(saved.folder.endsWith("t.jo7"))
        ctx.contentResolver.openInputStream(saved.uri!!)!!.use { assertTrue(it.readBytes().size == f.length().toInt()) }
    }

    @Test fun updateFeedIsReachableAndParsesWithTheOldContract() = runBlocking {
        val info = try { UpdateRepository(ctx).fetchLatest() } catch (e: java.io.IOException) { org.junit.Assume.assumeNoException(e); null }
        assertNotNull(info); assertTrue(info!!.versionCode >= 26); assertTrue(info.apkUrl.startsWith(UpdateContract.APK_PREFIX))
    }

    @Test fun updaterKnowsTheInstalledVersion() {
        val u = UpdateRepository(ctx); assertTrue(u.currentVersionCode() >= 1); assertTrue(u.currentVersionName().startsWith("build"))
    }
}
