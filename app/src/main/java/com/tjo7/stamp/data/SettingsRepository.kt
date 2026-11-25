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
        val width = intPreferencesKey("width"); val margin = intPreferencesKey("margin")
        val opacity = intPreferencesKey("opacity"); val shadow = intPreferencesKey("shadow")
        val white = booleanPreferencesKey("white"); val bold = booleanPreferencesKey("bold")
        val mode = stringPreferencesKey("mode"); val format = stringPreferencesKey("format")
        val size = stringPreferencesKey("size"); val audio = booleanPreferencesKey("audio")
        val crop = booleanPreferencesKey("stickerCrop"); val dark = booleanPreferencesKey("dark")
        val updSkip = intPreferencesKey("updSkip")
    }

    val settings: Flow<StampSettings> = ctx.store.data.map { read(it) }
    val darkTheme: Flow<Boolean> = ctx.store.data.map { it[K.dark] ?: true }
    val skippedVersion: Flow<Int> = ctx.store.data.map { it[K.updSkip] ?: 0 }

    suspend fun load(): StampSettings = settings.first()

    suspend fun save(s: StampSettings) {
        ctx.store.edit {
            it[K.text] = s.text; it[K.corner] = s.corner.key; it[K.width] = s.widthPct; it[K.margin] = s.marginPct
            it[K.opacity] = s.opacity; it[K.shadow] = s.shadow; it[K.white] = s.white; it[K.bold] = s.bold
            it[K.mode] = s.mode.key; it[K.format] = s.format.key; it[K.size] = s.size.key
            it[K.audio] = s.keepAudio; it[K.crop] = s.stickerCrop
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
                widthPct = p[K.width] ?: d.widthPct, marginPct = p[K.margin] ?: d.marginPct,
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
