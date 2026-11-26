package com.tjo7.stamp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tjo7.stamp.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "tjo7_settings")

/** Persisted preferences. The move-credit offset (dx/dy) is intentionally never stored. */
class SettingsRepository(private val ctx: Context) {
    private object K {
        val text = stringPreferencesKey("text"); val corner = stringPreferencesKey("corner")
        val width = intPreferencesKey("width"); val margin = intPreferencesKey("margin"); val marginF = floatPreferencesKey("marginF")
        val opacity = intPreferencesKey("opacity"); val shadow = intPreferencesKey("shadow")
        val white = booleanPreferencesKey("white"); val bold = booleanPreferencesKey("bold")
        val mode = stringPreferencesKey("mode"); val format = stringPreferencesKey("format")
        val size = stringPreferencesKey("size"); val audio = booleanPreferencesKey("audio")
        val crop = booleanPreferencesKey("stickerCrop"); val dark = booleanPreferencesKey("dark")
        val updSkip = intPreferencesKey("updSkip")
        val legacyDone = booleanPreferencesKey("legacyDone")
    }

    val settings: Flow<StampSettings> = ctx.store.data.map { read(it) }
    val darkTheme: Flow<Boolean> = ctx.store.data.map { it[K.dark] ?: true }
    val skippedVersion: Flow<Int> = ctx.store.data.map { it[K.updSkip] ?: 0 }

    suspend fun load(): StampSettings = settings.first()

    suspend fun save(s: StampSettings) {
        ctx.store.edit {
            it[K.text] = s.text; it[K.corner] = s.corner.key; it[K.width] = s.widthPct; it[K.marginF] = s.marginPct
            it[K.opacity] = s.opacity; it[K.shadow] = s.shadow; it[K.white] = s.white; it[K.bold] = s.bold
            it[K.mode] = s.mode.key; it[K.format] = s.format.key; it[K.size] = s.size.key
            it[K.audio] = s.keepAudio; it[K.crop] = s.stickerCrop
        }
    }

    suspend fun legacyDone(): Boolean = ctx.store.data.first()[K.legacyDone] == true

    /** One-time import of the old web build's saved settings. Never overrides values the new app already saved. */
    suspend fun importLegacy(l: LegacyImport.Legacy?) {
        ctx.store.edit { p ->
            if (p[K.legacyDone] == true) return@edit
            p[K.legacyDone] = true
            if (l == null) return@edit
            if (p[K.text] == null && p[K.corner] == null && l.settings != null) {
                val o = l.settings; val g = o.optJSONObject("g")
                fun num(j: org.json.JSONObject?, k: String, lo: Int, hi: Int): Int? =
                    j?.takeIf { it.has(k) && !it.isNull(k) }?.optDouble(k)?.takeIf { !it.isNaN() && it >= lo && it <= hi }?.toInt()
                Corner.from(g?.optString("corner"))?.let { p[K.corner] = it.key }
                num(g, "width", 1, 100)?.let { p[K.width] = it }
                g?.takeIf { it.has("margin") && !it.isNull("margin") }?.optDouble("margin")?.takeIf { !it.isNaN() && it >= 0 && it <= 100 }?.let { p[K.marginF] = it.toFloat() }
                num(g, "opacity", 0, 100)?.let { p[K.opacity] = it }
                num(g, "shadow", 0, 100)?.let { p[K.shadow] = it }
                g?.optInt("weight", 0)?.let { if (it == 400 || it == 700) p[K.bold] = it == 700 }
                g?.optString("color")?.let { if (it == "#ffffff" || it == "#000000") p[K.white] = it == "#ffffff" }
                CreditMode.from(g?.optString("mode"))?.let { p[K.mode] = it.key }
                o.optString("text").takeIf { it.isNotBlank() }?.let { p[K.text] = it.take(StampSettings.MAX_TEXT) }
                OutFormat.fromLegacy(o.optString("fmt"))?.let { p[K.format] = it.key }
                SizeSel.from(o.optString("sizeSel"))?.let { p[K.size] = it.key }
                if (o.has("audio") && !o.isNull("audio")) p[K.audio] = o.optBoolean("audio", true)
                if (o.has("crop") && !o.isNull("crop")) p[K.crop] = o.optBoolean("crop", true)
            }
            if (p[K.dark] == null) l.theme?.let { if (it == "light" || it == "dark") p[K.dark] = it == "dark" }
        }
    }

    suspend fun setDark(v: Boolean) { ctx.store.edit { it[K.dark] = v } }
    suspend fun skipVersion(code: Int) { ctx.store.edit { it[K.updSkip] = code } }

    suspend fun reset() {
        ctx.store.edit { p -> val skip = p[K.updSkip]; val dark = p[K.dark]; p.clear(); skip?.let { p[K.updSkip] = it }; dark?.let { p[K.dark] = it } }
    }

    companion object {
        fun read(p: Preferences): StampSettings {
            val d = StampSettings()
            return StampSettings(
                text = p[K.text]?.takeIf { it.isNotBlank() } ?: d.text,
                corner = Corner.from(p[K.corner]) ?: d.corner,
                widthPct = p[K.width] ?: d.widthPct, marginPct = p[K.marginF] ?: p[K.margin]?.toFloat() ?: d.marginPct,
                opacity = p[K.opacity] ?: d.opacity, shadow = p[K.shadow] ?: d.shadow,
                white = p[K.white] ?: d.white, bold = p[K.bold] ?: d.bold,
                mode = CreditMode.from(p[K.mode]) ?: d.mode,
                format = OutFormat.fromLegacy(p[K.format]) ?: d.format,
                size = SizeSel.from(p[K.size]) ?: d.size,
                keepAudio = p[K.audio] ?: d.keepAudio, stickerCrop = p[K.crop] ?: d.stickerCrop,
            ).sanitized()
        }
    }
}
