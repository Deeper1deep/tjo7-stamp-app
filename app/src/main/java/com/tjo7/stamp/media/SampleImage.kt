package com.tjo7.stamp.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/** The demo picture shown before any file is added (same drawing as the old web build's sample). */
object SampleImage {
    fun make(): Bitmap {
        val n = 1080
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, n.toFloat(), intArrayOf(0xFF2C3E66.toInt(), 0xFFD98A6A.toInt(), 0xFFF2C48B.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
        p.shader = null
        p.color = 0xFFF7E2B0.toInt(); c.drawCircle(700f, 560f, 120f, p)
        p.color = 0xFF5A3D4F.toInt()
        val a = Path().apply {
            moveTo(0f, 760f); cubicTo(250f, 640f, 450f, 800f, 700f, 700f); cubicTo(850f, 640f, 980f, 720f, 1080f, 690f)
            lineTo(1080f, 1080f); lineTo(0f, 1080f); close()
        }
        c.drawPath(a, p)
        p.color = 0xFF2B2236.toInt()
        val b = Path().apply { moveTo(0f, 880f); cubicTo(300f, 800f, 560f, 940f, 1080f, 840f); lineTo(1080f, 1080f); lineTo(0f, 1080f); close() }
        c.drawPath(b, p)
        return bmp
    }
}
