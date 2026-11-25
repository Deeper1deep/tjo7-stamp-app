package com.tjo7.stamp.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Shares finished files through the system share sheet (copied to cache/share and exposed by FileProvider). */
class ShareHelper(private val ctx: Context) {
    private val dir = File(ctx.cacheDir, "share").apply { mkdirs() }

    private fun uriFor(file: File): Uri {
        val copy = File(dir, file.name)
        if (copy.absolutePath != file.absolutePath) file.copyTo(copy, overwrite = true)
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", copy)
    }

    fun intent(files: List<Pair<File, String>>): Intent {
        val uris = ArrayList(files.map { uriFor(it.first) })
        val mime = files.map { it.second }.distinct().let { if (it.size == 1) it[0] else "*/*" }
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        i.type = mime
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(i, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}
