package com.tjo7.stamp.data

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/**
 * DOCUMENTED EXCEPTION to "no WebView": this object creates ONE hidden, never-attached WebView, only to read the old
 * Capacitor build's localStorage once (same package + same signing key keep that data across the update), and destroys it
 * right after. No WebView is ever used for UI or normal functionality; after the migration succeeds it is never created again.
 *
 * The previous (web/Capacitor) build kept its settings in the WebView's localStorage for the origin
 * https://localhost. Same package + same signature keep that data across the update, so a hidden WebView
 * on the same origin can read it once and hand it to [SettingsRepository.importLegacy].
 */
object LegacyImport {
    class Legacy(val settings: JSONObject?, val theme: String?) { val isEmpty get() = settings == null && theme == null }

    suspend fun read(ctx: Context): Legacy? = withTimeoutOrNull(7000) {
        suspendCancellableCoroutine { cont ->
            val wv = WebView(ctx)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            fun done(v: Legacy?) { if (cont.isActive) cont.resume(v); runCatching { wv.destroy() } }
            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
                    WebResourceResponse("text/html", "utf-8", ByteArrayInputStream("<html></html>".toByteArray()))

                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript(
                        "(function(){return JSON.stringify([localStorage.getItem('tjo7.settings.v1'),localStorage.getItem('tjo7.theme')])})()"
                    ) { raw -> done(parse(raw)) }
                }
            }
            cont.invokeOnCancellation { runCatching { wv.destroy() } }
            wv.loadUrl("https://localhost/")
        }
    }

    internal fun parse(raw: String?): Legacy? = runCatching {
        if (raw == null || raw == "null") return null
        val inner = JSONTokener(raw).nextValue() as? String ?: return null
        val arr = JSONArray(inner)
        val s = if (arr.isNull(0)) null else JSONObject(arr.getString(0))
        val t = if (arr.isNull(1)) null else arr.getString(1)
        Legacy(s, t)   // both null = the old page loaded fine and had nothing stored (a confirmed "no old data")
    }.getOrNull()
}
