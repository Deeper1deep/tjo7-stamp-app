package com.tjo7.stamp.ui

import android.graphics.Bitmap
import com.tjo7.stamp.data.UpdateInfo
import com.tjo7.stamp.domain.StampSettings
import com.tjo7.stamp.media.ExportOutput
import com.tjo7.stamp.media.SourceInfo

enum class Tab { EDITOR, CREDIT, OUTPUT, SETTINGS }

data class Item(val info: SourceInfo, val override: StampSettings? = null, val thumb: Bitmap? = null)

data class ResultEntry(
    val itemId: Long, val name: String, val output: ExportOutput?, val srcSize: Long,
    val savedTo: String?, val error: String?,
)

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val done: Long, val total: Long) : UpdateUi
    data class Ready(val info: UpdateInfo, val needsPermission: Boolean) : UpdateUi
    data class Failed(val reason: String) : UpdateUi
}

data class UiState(
    val tab: Tab = Tab.EDITOR,
    val items: List<Item> = emptyList(),
    val current: Int = 0,
    val global: StampSettings = StampSettings(),
    val preview: Bitmap? = null,
    val previewShow: Boolean = true,
    val previewLoading: Boolean = false,
    val busy: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val results: List<ResultEntry> = emptyList(),
    val message: String? = null,
    val update: UpdateUi = UpdateUi.Idle,
    val updateSheet: Boolean = false,
    val dark: Boolean = true,
    val moveOn: Boolean = false,
    val settingsLoaded: Boolean = false,
    val version: String = "",
) {
    val currentItem get() = items.getOrNull(current)

    /** Settings that apply to [item]: the global ones, with the per-file look when it has one. */
    fun effective(item: Item?): StampSettings {
        val ov = item?.override ?: return global
        return global.withLook(ov).copy(dx = ov.dx, dy = ov.dy)
    }

    val editing get() = effective(currentItem)
    val hasOverride get() = currentItem?.override != null
}
