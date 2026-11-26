package com.tjo7.stamp

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjo7.stamp.data.LegacyImport
import com.tjo7.stamp.data.SettingsRepository
import com.tjo7.stamp.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/** Simulates the old web build: writes localStorage on https://localhost, then checks the new app imports it. */
@RunWith(AndroidJUnit4::class)
class LegacyImportTest {
    private suspend fun writeOldLocalStorage(js: String) = suspendCancellableCoroutine<Unit> { cont ->
        val wv = WebView(Support.target)
        wv.settings.javaScriptEnabled = true; wv.settings.domStorageEnabled = true
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, r: WebResourceRequest?) =
                WebResourceResponse("text/html", "utf-8", ByteArrayInputStream("<html></html>".toByteArray()))
            override fun onPageFinished(view: WebView, url: String?) { view.evaluateJavascript(js) { cont.resume(Unit) } }
        }
        wv.loadUrl("https://localhost/")
    }

    @Test fun oldWebSettingsAreImportedOnce() = runBlocking(Dispatchers.Main) {
        val repo = SettingsRepository(Support.target)
        repo.reset()
        writeOldLocalStorage("""localStorage.setItem('tjo7.settings.v1', JSON.stringify({g:{corner:'tr',width:30,margin:5,opacity:80,shadow:40,weight:700,color:'#000000',mode:'blink',dx:3,dy:4},text:'@old',fmt:'webm',sizeSel:'720',audio:false,crop:false})); localStorage.setItem('tjo7.theme','light'); 1""")
        val legacy = LegacyImport.read(Support.target)
        assertNotNull(legacy)
        repo.importLegacy(legacy)
        val s = repo.load()
        assertEquals("@old", s.text); assertEquals(Corner.TR, s.corner); assertEquals(30, s.widthPct)
        assertEquals(5f, s.marginPct, 0f); assertEquals(80, s.opacity); assertEquals(40, s.shadow)
        assertTrue(s.bold); assertFalse(s.white); assertEquals(CreditMode.BLINK, s.mode)
        assertEquals(SizeSel.W720, s.size); assertFalse(s.keepAudio); assertFalse(s.stickerCrop)
        assertTrue(repo.legacyDone())
    }
}
