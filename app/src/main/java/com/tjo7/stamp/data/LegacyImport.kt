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
 * The previous (web/Capacitor) build kept its settings in the WebView's localStorage for the origin
 * https://localhost. Same package + same signature keep that data across the update, so a hidden WebView
 * on the same origin can read it once and hand it to [SettingsRepository.importLegacy].
 */
object LegacyImport {
    class Legacy(val settings: JSONObject?, val theme: String?)

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
        if (s == null && t == null) null else Legacy(s, t)
    }.getOrNull()
}
