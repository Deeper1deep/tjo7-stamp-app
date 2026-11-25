package com.tjo7.stamp.domain

/** Corner of the frame where the credit sits. */
enum class Corner(val key: String) {
    TL("tl"), TR("tr"), BL("bl"), BR("br");

    val top get() = this == TL || this == TR
    val left get() = this == TL || this == BL

    companion object {
        fun from(key: String?): Corner? = entries.firstOrNull { it.key == key }
    }
}

enum class CreditMode(val key: String) {
    STATIC("static"), BLINK("blink");

    companion object {
        fun from(key: String?): CreditMode? = entries.firstOrNull { it.key == key }
    }
}

/** Output container. AUTO keeps the source type. */
enum class OutFormat(val key: String, val ext: String, val mime: String) {
    AUTO("auto", "", ""),
    GIF("gif", "gif", "image/gif"),
    MP4("mp4", "mp4", "video/mp4"),
    PNG("png", "png", "image/png"),
    JPG("jpg", "jpg", "image/jpeg"),
    WEBP("webp", "webp", "image/webp");

    val isVideo get() = this == MP4

    companion object {
        fun from(key: String?): OutFormat? = entries.firstOrNull { it.key == key }
        /** Old web builds also stored "webm"; WebM video export is replaced by MP4 natively. */
        fun fromLegacy(key: String?): OutFormat? = if (key == "webm") MP4 else from(key)
    }
}

enum class SizeSel(val key: String, val width: Int) {
    ORIG("orig", 0), W1080("1080", 1080), W720("720", 720), W540("540", 540);

    companion object {
        fun from(key: String?): SizeSel? = entries.firstOrNull { it.key == key }
    }
}

enum class MediaKind { IMAGE, ANIM_GIF, ANIM_WEBP, VIDEO }

/**
 * Everything the user can tune. [dx]/[dy] (move-credit offset, in % of the frame) are
 * deliberately NOT persisted: they reset every time the app is reopened.
 */
data class StampSettings(
    val text: String = DEFAULT_TEXT,
    val corner: Corner = Corner.BL,
    val widthPct: Int = 13,
    val marginPct: Int = 2,
    val opacity: Int = 55,
    val shadow: Int = 0,
    val white: Boolean = true,
    val bold: Boolean = false,
    val mode: CreditMode = CreditMode.STATIC,
    val format: OutFormat = OutFormat.AUTO,
    val size: SizeSel = SizeSel.ORIG,
    val keepAudio: Boolean = true,
    val sticker: Boolean = false,
    val stickerCrop: Boolean = true,
    val transparent: Boolean = false,
    val dx: Float = 0f,
    val dy: Float = 0f,
) {
    val color get() = if (white) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    val weight get() = if (bold) 700 else 400

    /** Only the look of the credit (what a per-file override replaces). */
    fun lookEquals(o: StampSettings) =
        corner == o.corner && widthPct == o.widthPct && marginPct == o.marginPct && opacity == o.opacity &&
            shadow == o.shadow && white == o.white && bold == o.bold && mode == o.mode

    fun withLook(o: StampSettings) = copy(
        corner = o.corner, widthPct = o.widthPct, marginPct = o.marginPct, opacity = o.opacity,
        shadow = o.shadow, white = o.white, bold = o.bold, mode = o.mode,
    )

    fun applyPreset(p: Preset): StampSettings = when (p) {
        Preset.CREDIT -> copy(mode = CreditMode.STATIC, widthPct = 13, marginPct = 2, opacity = 55, bold = false)
        Preset.BLINK -> copy(mode = CreditMode.BLINK, widthPct = 24, marginPct = 3, opacity = 100, bold = true)
    }

    /** Clamp every field into its valid range (used for data loaded from disk). */
    fun sanitized() = copy(
        text = text.take(MAX_TEXT),
        widthPct = widthPct.coerceIn(1, 100), marginPct = marginPct.coerceIn(0, 100),
        opacity = opacity.coerceIn(0, 100), shadow = shadow.coerceIn(0, 100),
        dx = dx.coerceIn(-100f, 100f), dy = dy.coerceIn(-100f, 100f),
    )

    companion object {
        const val DEFAULT_TEXT = "@t.jo7"
        const val MAX_TEXT = 60
    }
}

enum class Preset { CREDIT, BLINK }

/** Whether the credit is visible at time [t] seconds. Blink: shown during the 2nd second of every 3. */
fun creditVisibleAt(mode: CreditMode, t: Double): Boolean {
    if (mode == CreditMode.STATIC) return true
    val m = ((t % 3.0) + 3.0) % 3.0
    return m >= 0.999 && m < 1.999
}

/** WhatsApp sticker limits. */
object WhatsApp {
    const val STATIC_MAX = 100 * 1024
    const val ANIM_MAX = 500 * 1024
    const val MAX_SEC = 10.0
    const val MIN_DELAY = 0.02
    const val SIZE = 512
}

/** Output size rule (same as the web version): never upscale; cap motion at ~8.3 MP. */
fun outputSize(srcW: Int, srcH: Int, sel: SizeSel, motion: Boolean): Pair<Int, Int> {
    val tw = if (sel == SizeSel.ORIG) srcW.toDouble() else minOf(sel.width.toDouble(), srcW.toDouble())
    var w = tw
    var h = tw * srcH / srcW
    if (motion && w * h > 8.3e6) {
        val k = Math.sqrt(8.3e6 / (w * h)); w *= k; h *= k
    }
    return Pair(maxOf(2, Math.round(w).toInt()), maxOf(2, Math.round(h).toInt()))
}
