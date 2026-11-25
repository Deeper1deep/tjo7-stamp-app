package com.tjo7.stamp.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

class SaveException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

/** Saves finished files into the gallery (Pictures/t.jo7, Movies/t.jo7) or Downloads/t.jo7 via MediaStore. */
class MediaSaver(private val ctx: Context) {
    class Saved(val uri: Uri?, val folder: String)

    /** Below Android 10 public folders need the legacy storage permission. */
    val needsLegacyPermission get() = Build.VERSION.SDK_INT < 29

    fun save(file: File, displayName: String, mime: String): Saved {
        val isVideo = mime.startsWith("video/")
        val isImage = mime.startsWith("image/")
        val folder = when { isVideo -> Environment.DIRECTORY_MOVIES; isImage -> Environment.DIRECTORY_PICTURES; else -> Environment.DIRECTORY_DOWNLOADS }
        val rel = "$folder/t.jo7"
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = when {
                isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(collection, cv) ?: throw SaveException("insert failed")
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o, 1 shl 16) } }
                    ?: throw SaveException("cannot open output")
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                ctx.contentResolver.update(uri, done, null, null)
            } catch (e: Exception) {
                try { ctx.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
                throw if (e is SaveException) e else SaveException(e.message ?: "save failed", e)
            }
            return Saved(uri, rel)
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(folder), "t.jo7")
        if (!dir.exists() && !dir.mkdirs()) throw SaveException("cannot create folder")
        var target = File(dir, displayName)
        var i = 1
        while (target.exists()) target = File(dir, displayName.substringBeforeLast('.') + "-" + (i++) + "." + displayName.substringAfterLast('.', ""))
        try { file.copyTo(target) } catch (e: Exception) { throw SaveException(e.message ?: "save failed", e) }
        android.media.MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf(mime), null)
        return Saved(Uri.fromFile(target), rel)
    }
}
