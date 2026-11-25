package com.tjo7.stamp.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtil {
    class Entry(val name: String, val file: File)

    const val MAX_ENTRIES = 600
    const val MAX_ENTRY_BYTES = 40L * 1024 * 1024

    /** Writes [entries] into a ZIP stream. Already-compressed media is stored, not deflated twice. */
    fun write(entries: List<Pair<String, File>>, out: OutputStream) {
        ZipOutputStream(out).use { z ->
            val used = HashSet<String>()
            for ((n0, f) in entries) {
                var n = n0.replace('\\', '/').trimStart('/')
                var i = 1
                while (!used.add(n)) n = n0.substringBeforeLast('.') + "-" + (i++) + "." + n0.substringAfterLast('.', "")
                z.putNextEntry(ZipEntry(n))
                f.inputStream().use { it.copyTo(z, 1 shl 16) }
                z.closeEntry()
            }
        }
    }

    /** Extracts every file entry of a ZIP into [dir] (zip-slip safe, size capped). Returns (entry name, file). */
    fun extract(input: InputStream, dir: File): List<Pair<String, File>> {
        dir.mkdirs()
        val out = ArrayList<Pair<String, File>>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            var n = 0
            while (e != null) {
                if (!e.isDirectory) {
                    if (++n > MAX_ENTRIES) throw IllegalArgumentException("too many entries")
                    val leaf = e.name.substringAfterLast('/').substringAfterLast('\\')
                    if (leaf.isNotEmpty() && !leaf.startsWith(".")) {
                        var f = File(dir, leaf); var k = 1
                        while (f.exists()) f = File(dir, (k++).toString() + "-" + leaf)
                        if (!f.canonicalPath.startsWith(dir.canonicalPath)) throw SecurityException("bad entry")
                        var total = 0L
                        f.outputStream().use { o ->
                            val buf = ByteArray(1 shl 16)
                            while (true) {
                                val r = z.read(buf); if (r < 0) break
                                total += r; if (total > MAX_ENTRY_BYTES) throw IllegalArgumentException("entry too large")
                                o.write(buf, 0, r)
                            }
                        }
                        out.add(e.name to f)
                    }
                }
                e = z.nextEntry
            }
        }
        return out
    }
}
