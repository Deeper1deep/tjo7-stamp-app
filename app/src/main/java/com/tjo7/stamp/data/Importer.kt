package com.tjo7.stamp.data

import android.content.Context
import android.net.Uri
import com.tjo7.stamp.media.PackRef
import com.tjo7.stamp.media.SourceInfo
import com.tjo7.stamp.media.SourceLoader
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Turns picked / shared URIs into [SourceInfo]s. ZIP / .wastickers / .enc sticker packs are unpacked into the cache. */
class Importer(private val ctx: Context) {
    class Result(val items: List<SourceInfo>, val failed: Int)

    private val ids = AtomicLong(1)
    private val work = File(ctx.cacheDir, "work").apply { mkdirs() }

    fun clearWork() { work.deleteRecursively(); work.mkdirs() }

    private fun isPack(name: String, head: ByteArray): Boolean {
        val ext = SourceLoader.extOf(name)
        val zipMagic = head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        return zipMagic && (ext == "zip" || ext == "wastickers" || ext == "enc" || ext == "")
    }

    /** [copy]: copy each file into the cache first (needed for share-sheet URIs whose permission can expire). */
    fun import(uris: List<Uri>, copy: Boolean): Result {
        val items = ArrayList<SourceInfo>(); var failed = 0
        for (u in uris) {
            try {
                val (name, _) = SourceLoader.displayName(ctx, u)
                val head = SourceLoader.open(ctx, u).use { s -> val b = ByteArray(4); val r = s.read(b); b.copyOf(maxOf(r, 0)) }
                if (isPack(name, head)) { val r = importPack(u, name); if (r.isEmpty()) failed++ else items.addAll(r); continue }
                val use = if (copy) copyToCache(u, name) else u
                items.add(SourceLoader.probe(ctx, use, ids.getAndIncrement(), nameOverride = name))
            } catch (e: Exception) { failed++ }
        }
        return Result(items, failed)
    }

    private fun copyToCache(u: Uri, name: String): Uri {
        val d = File(work, "s" + ids.get()).apply { mkdirs() }
        val f = File(d, name.replace('/', '_'))
        SourceLoader.open(ctx, u).use { i -> f.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
        return Uri.fromFile(f)
    }

    private fun importPack(u: Uri, name: String): List<SourceInfo> {
        val dir = File(work, "pack" + ids.get()).apply { mkdirs() }
        val files = SourceLoader.open(ctx, u).use { ZipUtil.extract(it, dir) }
        val webps = files.filter { it.first.endsWith(".webp", true) }
        if (webps.isEmpty()) return emptyList()
        val pack = PackRef(name.substringBeforeLast('.').take(40).ifEmpty { "pack" }, files.filter { !it.first.endsWith(".webp", true) }.map { it.first to it.second })
        return webps.mapNotNull { (entry, f) ->
            try { SourceLoader.probe(ctx, Uri.fromFile(f), ids.getAndIncrement(), pack, entry, f.name) } catch (_: Exception) { null }
        }
    }
}
