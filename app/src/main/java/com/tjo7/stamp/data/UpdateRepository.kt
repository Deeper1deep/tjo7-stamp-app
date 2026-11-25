package com.tjo7.stamp.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int, val versionName: String, val size: Long,
    val notes: List<String>, val apkUrl: String, val sha256: String?,
)

/** Same contract as the old web build: latest.json in the public releases repo. */
object UpdateContract {
    const val LATEST_URL = "https://github.com/Deeper1deep/tjo7-stamp-releases/releases/latest/download/latest.json"
    const val APK_PREFIX = "https://github.com/Deeper1deep/tjo7-stamp-releases/releases/download/"
    const val MAX_JSON = 256 * 1024

    fun parse(json: String): UpdateInfo? = try {
        val o = JSONObject(json)
        val code = o.getInt("versionCode")
        val apk = o.getString("apk")
        if (!apk.startsWith(APK_PREFIX)) null
        else {
            val notes = o.optJSONArray("notes")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            UpdateInfo(code, o.optString("versionName", "build $code"), o.optLong("size", 0), notes, apk,
                o.optString("sha256", "").lowercase().takeIf { it.length == 64 })
        }
    } catch (_: Exception) { null }

    fun isNewer(remote: Int, current: Int, skipped: Int = 0) = remote > current && remote != skipped
}

class UpdateException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

class UpdateRepository(private val ctx: Context) {
    private val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }

    fun currentVersionCode(): Int = packageInfo(ctx.packageName, 0).let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode
    }

    fun currentVersionName(): String = packageInfo(ctx.packageName, 0).versionName ?: ""

    @Suppress("DEPRECATION")
    private fun packageInfo(pkg: String, flags: Int): PackageInfo = ctx.packageManager.getPackageInfo(pkg, flags)

    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = URL(UpdateContract.LATEST_URL).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000; c.instanceFollowRedirects = true
            if (c.responseCode != 200) throw UpdateException("HTTP " + c.responseCode)
            val buf = c.inputStream.use { it.readNBytesCompat(UpdateContract.MAX_JSON + 1) }
            if (buf.size > UpdateContract.MAX_JSON) throw UpdateException("too large")
            UpdateContract.parse(String(buf, Charsets.UTF_8))
        } finally { c.disconnect() }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val o = java.io.ByteArrayOutputStream(); val b = ByteArray(8192)
        while (o.size() < max) { val r = read(b, 0, minOf(b.size, max - o.size())); if (r < 0) break; o.write(b, 0, r) }
        return o.toByteArray()
    }

    /** Downloads the APK to cache/updates, reporting (done, total). Verifies size, SHA-256 (if published) and signer. */
    suspend fun download(info: UpdateInfo, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val part = File(dir, "tjo7-update.apk.part"); val dest = File(dir, "tjo7-update.apk")
        part.delete(); dest.delete()
        val c = URL(info.apkUrl).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 30_000; c.instanceFollowRedirects = true
            if (c.responseCode != 200) throw UpdateException("HTTP " + c.responseCode)
            val total = if (c.contentLength > 0) c.contentLength.toLong() else info.size
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            c.inputStream.use { i -> part.outputStream().use { o ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val r = i.read(buf); if (r < 0) break
                    o.write(buf, 0, r); md.update(buf, 0, r); done += r; onProgress(done, total)
                }
            } }
            if (done < 100 * 1024) throw UpdateException("file too small")
            if (info.size > 0 && done != info.size) throw UpdateException("size mismatch")
            info.sha256?.let { exp -> val got = md.digest().joinToString("") { "%02x".format(it) }; if (got != exp) throw UpdateException("checksum mismatch") }
            if (!part.renameTo(dest)) throw UpdateException("cannot finalize")
            verifyPackage(dest, info)
            dest
        } catch (e: Exception) { part.delete(); throw e }
        finally { c.disconnect() }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(apk: File, info: UpdateInfo) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pi = ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: throw UpdateException("not an apk")
        if (pi.packageName != ctx.packageName) throw UpdateException("wrong package")
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
        if (code != info.versionCode) throw UpdateException("version mismatch")
        val mine = signers(packageInfo(ctx.packageName, flags)); val theirs = signers(pi)
        if (mine.isNotEmpty() && theirs.isNotEmpty() && mine.intersect(theirs.toSet()).isEmpty()) throw UpdateException("different signing certificate")
    }

    @Suppress("DEPRECATION")
    private fun signers(pi: PackageInfo): List<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) pi.signingInfo?.apkContentsSigners else pi.signatures
        return sigs?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } } ?: emptyList()
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun cleanup() { dir.listFiles()?.forEach { it.delete() } }
}
