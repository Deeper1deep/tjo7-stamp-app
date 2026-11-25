package com.tjo7.stamp

import com.tjo7.stamp.data.UpdateContract
import com.tjo7.stamp.data.ZipUtil
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UpdateAndZipTest {
    @get:Rule val tmp = TemporaryFolder()

    private val good = """{"versionCode":27,"versionName":"build 27","size":12345678,"notes":["a","b"],
        "apk":"https://github.com/Deeper1deep/tjo7-stamp-releases/releases/download/build-27/tjo7-stamp.apk"}"""

    @Test fun parsesTheExistingLatestJsonContract() {
        val u = UpdateContract.parse(good)!!
        assertEquals(27, u.versionCode); assertEquals("build 27", u.versionName); assertEquals(12345678L, u.size); assertEquals(listOf("a", "b"), u.notes)
        assertNull(u.sha256)
    }

    @Test fun parsesOptionalSha256() {
        val h = "a".repeat(64)
        assertEquals(h, UpdateContract.parse(good.replace("\"size\"", "\"sha256\":\"$h\",\"size\""))!!.sha256)
    }

    @Test fun rejectsForeignApkUrlAndGarbage() {
        assertNull(UpdateContract.parse(good.replace("Deeper1deep/tjo7-stamp-releases", "evil/repo")))
        assertNull(UpdateContract.parse(good.replace("https://", "http://")))
        assertNull(UpdateContract.parse("not json")); assertNull(UpdateContract.parse("{}"))
    }

    @Test fun versionComparison() {
        assertTrue(UpdateContract.isNewer(27, 26)); assertFalse(UpdateContract.isNewer(26, 26)); assertFalse(UpdateContract.isNewer(25, 26))
        assertFalse(UpdateContract.isNewer(27, 26, skipped = 27)); assertTrue(UpdateContract.isNewer(28, 26, skipped = 27))
    }

    @Test fun zipRoundTrip() {
        val a = tmp.newFile("a.webp").apply { writeBytes(ByteArray(1000) { it.toByte() }) }
        val b = tmp.newFile("b.txt").apply { writeText("hello") }
        val bo = ByteArrayOutputStream(); ZipUtil.write(listOf("01-a.webp" to a, "b.txt" to b, "b.txt" to b), bo)
        val dir = tmp.newFolder("out")
        val got = ZipUtil.extract(ByteArrayInputStream(bo.toByteArray()), dir)
        assertEquals(3, got.size)
        assertArrayEquals(a.readBytes(), got.first { it.first == "01-a.webp" }.second.readBytes())
    }

    @Test fun zipSlipIsNeutralised() {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z -> z.putNextEntry(ZipEntry("../../evil.webp")); z.write(1); z.closeEntry() }
        val dir = tmp.newFolder("safe")
        val got = ZipUtil.extract(ByteArrayInputStream(bo.toByteArray()), dir)
        assertEquals(1, got.size)
        assertTrue(got[0].second.canonicalPath.startsWith(dir.canonicalPath))
    }
}
