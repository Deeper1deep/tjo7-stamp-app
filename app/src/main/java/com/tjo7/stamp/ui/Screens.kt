package com.tjo7.stamp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tjo7.stamp.R
import com.tjo7.stamp.domain.*

// ------------------------------------------------------------------ preview
@Composable
fun PreviewBox(st: UiState, vm: MainViewModel, maxHeight: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val bmp = st.preview
    Box(modifier.fillMaxWidth().heightIn(max = maxHeight).clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bmp == null || (st.items.isEmpty() && !st.global.transparent)) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (st.previewLoading) CircularProgressIndicator() else {
                    Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.empty_sub), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = maxHeight), contentAlignment = Alignment.Center) {
                val ratio = bmp.width.toFloat() / bmp.height
                val w = minOf(this.maxWidth, this.maxHeight * ratio)
                val widthPx = with(LocalDensity.current) { w.toPx() }
                val heightPx = widthPx / ratio
                val dragMod = if (st.moveOn && !st.global.transparent) Modifier.pointerInput(widthPx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        vm.nudge(drag.x / widthPx * 100f, drag.y / heightPx * 100f)
                    }
                } else Modifier
                Box(Modifier.width(w).aspectRatio(ratio).then(dragMod)) {
                    if (st.global.transparent) Box(Modifier.matchParentSize().background(Color(0xFF8A8FA8)))
                    Image(bmp.asImageBitmap(), null, Modifier.matchParentSize().testTag("preview"), contentScale = ContentScale.FillBounds)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ editor
@Composable
fun EditorTab(st: UiState, vm: MainViewModel, onPick: () -> Unit, onExport: (Boolean) -> Unit, onShare: (List<ResultEntry>) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Pad), verticalArrangement = Arrangement.spacedBy(Gap)) {
        PreviewBox(st, vm, 360.dp)
        if (st.editing.mode == CreditMode.BLINK && st.items.isNotEmpty()) {
            OutlinedButton(onClick = { vm.setPreviewShow(!st.previewShow) }, Modifier.fillMaxWidth()) {
                Text(stringResource(if (st.previewShow) R.string.hide_credit else R.string.show_credit))
            }
        }
        if (st.currentItem?.info?.kind == com.tjo7.stamp.domain.MediaKind.VIDEO)
            Text(stringResource(R.string.preview_note_video), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        FilledTonalButton(onClick = onPick, Modifier.fillMaxWidth().height(52.dp).testTag("add_files")) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_files))
        }

        if (st.items.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(st.items, key = { _, it -> it.info.id }) { i, it ->
                    val on = i == st.current
                    Box(Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (on) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)) else Modifier)
                        .clickable { vm.select(i) }) {
                        it.thumb?.let { b -> Image(b.asImageBitmap(), it.info.name, Modifier.fillMaxSize().padding(if (on) 3.dp else 0.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
                        IconButton(onClick = { vm.remove(i) }, Modifier.align(Alignment.TopEnd).size(28.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.remove), tint = Color.White,
                                modifier = Modifier.size(16.dp).background(Color(0x99000000), RoundedCornerShape(8.dp)))
                        }
                    }
                }
            }
        }

        if (st.busy) {
            Section(stringResource(R.string.tab_editor)) {
                LinearProgressIndicator(progress = { st.progress }, Modifier.fillMaxWidth())
                Text(st.progressLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                OutlinedButton(onClick = vm::cancelExport, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        } else {
            val canExport = st.items.isNotEmpty() || st.global.transparent
            Button(onClick = { onExport(false) }, enabled = canExport, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("export_one")) {
                Text(stringResource(if (st.global.transparent) R.string.export_overlay else R.string.export_current))
            }
            if (st.items.size > 1 && !st.global.transparent)
                OutlinedButton(onClick = { onExport(true) }, Modifier.fillMaxWidth().height(50.dp).testTag("export_all")) {
                    Text(stringResource(R.string.export_all, st.items.size))
                }
        }

        if (st.results.isNotEmpty()) ResultsSection(st, vm, onShare)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ResultsSection(st: UiState, vm: MainViewModel, onShare: (List<ResultEntry>) -> Unit) {
    Section(stringResource(R.string.results)) {
        for (r in st.results) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                val o = r.output
                if (o != null) {
                    Text(stringResource(R.string.size_change, fmtBytes(r.srcSize), fmtBytes(o.size)), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (o.overLimit) Pill(stringResource(R.string.over_limit_tag), Tokens.Warn)
                        o.cut?.let { Pill(stringResource(R.string.cut_tag, it.first.toInt(), it.second.toInt()), Tokens.Warn) }
                        r.savedTo?.let { Pill(stringResource(R.string.saved_in, it), Tokens.Ok) }
                        r.error?.let { Pill(it, Tokens.Danger) }
                    }
                    TextButton(onClick = { onShare(listOf(r)) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share)) }
                } else {
                    Pill(r.error ?: "", Tokens.Danger)
                    TextButton(onClick = { vm.retry(r.itemId) }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.retry)) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
        val good = st.results.filter { it.output != null }
        if (good.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onShare(good) }, Modifier.weight(1f)) { Text(stringResource(R.string.share_all)) }
            OutlinedButton(onClick = vm::saveZip, Modifier.weight(1f)) { Text(stringResource(R.string.save_zip)) }
        }
    }
}

// ------------------------------------------------------------------ credit
@Composable
fun CreditTab(st: UiState, vm: MainViewModel) {
    val s = st.editing
    Column(Modifier.fillMaxSize()) {
        PreviewBox(st, vm, 190.dp, Modifier.padding(horizontal = Pad, vertical = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Pad), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Section(stringResource(R.string.credit_text)) {
                OutlinedTextField(value = s.text, onValueChange = { v -> vm.edit { it.copy(text = v.take(StampSettings.MAX_TEXT)) } },
                    modifier = Modifier.fillMaxWidth().testTag("credit_text"), singleLine = true)
                Text(stringResource(R.string.credit_presets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = { vm.applyPreset(Preset.CREDIT) }, label = { Text(stringResource(R.string.preset_credit)) })
                    FilterChip(selected = false, onClick = { vm.applyPreset(Preset.BLINK) }, label = { Text(stringResource(R.string.preset_blink)) })
                }
            }
            Section(stringResource(R.string.credit_corner)) {
                val rows = listOf(listOf(Corner.TL to R.string.corner_tl, Corner.TR to R.string.corner_tr), listOf(Corner.BL to R.string.corner_bl, Corner.BR to R.string.corner_br))
                for (r in rows) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((c, label) in r) FilterChip(selected = s.corner == c, onClick = { vm.edit { it.copy(corner = c, dx = 0f, dy = 0f) } },
                        label = { Text(stringResource(label), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, modifier = Modifier.weight(1f))
                }
            }
            Section(stringResource(R.string.credit_size)) {
                LabeledSlider(stringResource(R.string.credit_size), s.widthPct, 3..60) { v -> vm.edit { it.copy(widthPct = v) } }
                LabeledSlider(stringResource(R.string.credit_margin), s.marginPct, 0..15) { v -> vm.edit { it.copy(marginPct = v) } }
                LabeledSlider(stringResource(R.string.credit_opacity), s.opacity, 5..100) { v -> vm.edit { it.copy(opacity = v) } }
                LabeledSlider(stringResource(R.string.credit_shadow), s.shadow, 0..100) { v -> vm.edit { it.copy(shadow = v) } }
            }
            Section(stringResource(R.string.credit_color)) {
                Segmented(listOf(true to stringResource(R.string.color_white), false to stringResource(R.string.color_black)), s.white) { v -> vm.edit { it.copy(white = v) } }
                Text(stringResource(R.string.credit_weight), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Segmented(listOf(false to stringResource(R.string.weight_regular), true to stringResource(R.string.weight_bold)), s.bold) { v -> vm.edit { it.copy(bold = v) } }
                Text(stringResource(R.string.credit_mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Segmented(listOf(CreditMode.STATIC to stringResource(R.string.mode_static), CreditMode.BLINK to stringResource(R.string.mode_blink)), s.mode) { v -> vm.edit { it.copy(mode = v) } }
            }
            Section(stringResource(R.string.move_credit)) {
                SwitchRow(stringResource(R.string.move_credit), stringResource(R.string.move_hint), st.moveOn) { vm.setMove(it) }
                if (st.moveOn) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RepeatButton("▲") { vm.nudge(0f, -0.5f) }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RepeatButton("◀") { vm.nudge(-0.5f, 0f) }
                            Spacer(Modifier.size(52.dp))
                            RepeatButton("▶") { vm.nudge(0.5f, 0f) }
                        }
                        RepeatButton("▼") { vm.nudge(0f, 0.5f) }
                        TextButton(onClick = vm::resetMove) { Text(stringResource(R.string.move_reset)) }
                    }
                }
            }
            if (st.items.size > 1) Section(stringResource(R.string.per_file)) {
                SwitchRow(stringResource(R.string.per_file), stringResource(R.string.per_file_hint), st.hasOverride) { vm.toggleOverride() }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ------------------------------------------------------------------ output
@Composable
fun OutputTab(st: UiState, vm: MainViewModel) {
    val s = st.global
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Pad), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Section(stringResource(R.string.out_format)) {
            val fmts = listOf(
                Triple(OutFormat.AUTO, stringResource(R.string.fmt_auto_t), stringResource(R.string.fmt_auto_sub)),
                Triple(OutFormat.GIF, "GIF", stringResource(R.string.fmt_gif_sub)),
                Triple(OutFormat.MP4, "MP4", stringResource(R.string.fmt_mp4_sub)),
                Triple(OutFormat.PNG, "PNG", stringResource(R.string.fmt_png_sub)),
                Triple(OutFormat.JPG, "JPG", stringResource(R.string.fmt_jpg_sub)),
                Triple(OutFormat.WEBP, "WebP", stringResource(R.string.fmt_webp_sub)),
            )
            for (row in fmts.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((f, t, sub) in row) FormatChip(t, sub, s.format == f, Modifier.weight(1f)) { vm.edit { it.copy(format = f) } }
            }
            Text(stringResource(R.string.out_video_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section(stringResource(R.string.out_size)) {
            Segmented(listOf(SizeSel.ORIG to stringResource(R.string.size_orig), SizeSel.W1080 to "1080", SizeSel.W720 to "720", SizeSel.W540 to "540"), s.size) { v -> vm.edit { it.copy(size = v) } }
            SwitchRow(stringResource(R.string.out_audio), null, s.keepAudio) { v -> vm.edit { it.copy(keepAudio = v) } }
        }
        Section(stringResource(R.string.out_sticker)) {
            SwitchRow(stringResource(R.string.out_sticker), stringResource(R.string.out_sticker_hint), s.sticker) { v -> vm.edit { it.copy(sticker = v) } }
            if (s.sticker) SwitchRow(stringResource(R.string.out_crop), null, s.stickerCrop) { v -> vm.edit { it.copy(stickerCrop = v) } }
        }
        Section(stringResource(R.string.out_transparent)) {
            SwitchRow(stringResource(R.string.out_transparent), stringResource(R.string.out_transparent_hint), s.transparent) { v -> vm.edit { it.copy(transparent = v) } }
        }
    }
}

// ------------------------------------------------------------------ settings
@Composable
fun SettingsTab(st: UiState, vm: MainViewModel, onLink: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Pad), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Section(stringResource(R.string.set_update)) {
            Text(stringResource(R.string.set_version, st.version), style = MaterialTheme.typography.bodyMedium)
            when (val u = st.update) {
                is UpdateUi.Checking -> Text(stringResource(R.string.set_checking))
                is UpdateUi.UpToDate -> Text(stringResource(R.string.set_uptodate), color = Tokens.Ok)
                is UpdateUi.Available -> Button(onClick = { vm.openUpdateSheet(true) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.upd_available) + " — " + u.info.versionName) }
                else -> {}
            }
            OutlinedButton(onClick = { vm.checkUpdate(true) }, Modifier.fillMaxWidth().testTag("check_update")) { Text(stringResource(R.string.set_check)) }
        }
        Section(stringResource(R.string.set_appearance)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeCard(stringResource(R.string.theme_dark), true, st.dark, Modifier.weight(1f)) { vm.setDark(true) }
                ThemeCard(stringResource(R.string.theme_light), false, !st.dark, Modifier.weight(1f)) { vm.setDark(false) }
            }
        }
        Section("") {
            ListRow(stringResource(R.string.set_reset_items), stringResource(R.string.set_reset_items_sub)) { vm.resetAllItems() }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ListRow(stringResource(R.string.set_reset_defaults), stringResource(R.string.set_reset_defaults_sub)) { vm.resetDefaults() }
        }
        Section(stringResource(R.string.set_about)) {
            Text(stringResource(R.string.set_offline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.set_made_by), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onLink).padding(vertical = 6.dp))
        }
    }
}

// ------------------------------------------------------------------ update sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(st: UiState, vm: MainViewModel, onInstall: () -> Unit) {
    if (!st.updateSheet) return
    ModalBottomSheet(onDismissRequest = { vm.openUpdateSheet(false) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Pad).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(Gap)) {
            when (val u = st.update) {
                is UpdateUi.Available -> {
                    Text(stringResource(R.string.upd_available), style = MaterialTheme.typography.titleLarge)
                    Text(u.info.versionName + if (u.info.size > 0) " · " + fmtBytes(u.info.size) else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    u.info.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::skipUpdate, Modifier.weight(1f)) { Text(stringResource(R.string.upd_later)) }
                        Button(onClick = vm::downloadUpdate, Modifier.weight(1f).testTag("upd_go")) { Text(stringResource(R.string.upd_go)) }
                    }
                }
                is UpdateUi.Downloading -> {
                    Text(stringResource(R.string.upd_downloading), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.upd_keep_open), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val f = if (u.total > 0) (u.done.toFloat() / u.total).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { f }, Modifier.fillMaxWidth())
                    Text("${(f * 100).toInt()}% · ${fmtBytes(u.done)}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = vm::cancelDownload, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
                }
                is UpdateUi.Ready -> {
                    Text(stringResource(R.string.upd_ready), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.upd_permission), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onInstall, Modifier.fillMaxWidth().testTag("upd_install")) { Text(stringResource(R.string.upd_install)) }
                }
                is UpdateUi.Failed -> {
                    Text(stringResource(R.string.upd_failed), style = MaterialTheme.typography.titleLarge)
                    Text(u.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.openUpdateSheet(false) }, Modifier.weight(1f)) { Text(stringResource(R.string.close)) }
                        Button(onClick = { vm.checkUpdate(true) }, Modifier.weight(1f)) { Text(stringResource(R.string.upd_retry)) }
                    }
                }
                else -> Text(stringResource(R.string.set_checking))
            }
        }
    }
}
