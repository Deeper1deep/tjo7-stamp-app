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
import androidx.compose.ui.unit.sp
import com.tjo7.stamp.R
import com.tjo7.stamp.domain.*


// ------------------------------------------------------------------ stage (preview + transport)

/** Seconds on the 0-3s preview loop; the credit blinks against it. */
@Composable
private fun rememberLoopClock(): State<Float> {
    val t = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val t0 = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val v = ((now - t0) / 1e9f) % 3f
            if (kotlin.math.abs(v - t.value) >= 0.05f || v < t.value) t.value = v
        }
    }
    return t
}

@Composable
fun Stage(st: UiState, vm: MainViewModel, clock: State<Float>, compact: Boolean, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val maxH = (screenH * (if (compact) 0.24f else 0.46f)).dp
    val stageBg = Color(0xFF05060D)
    val on = st.preview
    val showOn = st.editing.mode != CreditMode.BLINK || creditVisibleAt(CreditMode.BLINK, clock.value.toDouble())
    val bmp = if (showOn) on else (st.previewOff ?: on)
    val empty = st.items.isEmpty()
    Box(Modifier.fillMaxWidth().heightIn(min = if (compact) 0.dp else 260.dp).clip(RoundedCornerShape(if (compact) 22.dp else 24.dp)).background(stageBg)
        .padding(if (compact) 8.dp else 10.dp), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = maxH), contentAlignment = Alignment.Center) {
                val ratio = bmp.width.toFloat() / bmp.height
                val w = minOf(this.maxWidth, maxH * ratio)
                val widthPx = with(LocalDensity.current) { w.toPx() }
                val heightPx = widthPx / ratio
                val dragMod = if (st.moveOn && !st.global.transparent && !empty) Modifier.pointerInput(widthPx) {
                    detectDragGestures { change, drag -> change.consume(); vm.nudge(drag.x / widthPx * 100f, drag.y / heightPx * 100f) }
                } else Modifier
                Box(Modifier.width(w).aspectRatio(ratio).clip(RoundedCornerShape(14.dp)).then(dragMod)) {
                    if (st.global.transparent || st.currentItem?.info?.hasAlpha == true) Checker(Modifier.matchParentSize())
                    Image(bmp.asImageBitmap(), null, Modifier.matchParentSize().testTag("preview"), contentScale = ContentScale.FillBounds)
                }
            }
        } else if (st.previewLoading) CircularProgressIndicator()
        val it0 = st.currentItem
        if (it0 != null && !st.global.transparent) {
            val i = it0.info
            Text(i.width.toString() + "×" + i.height + " · " + fmtBytes(i.sizeBytes) + if (i.durationMs > 0) " · " + fmtClock(i.durationMs / 1000.0) else "",
                color = Color.White, fontSize = 11.5.sp, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .background(Color(0x8C000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        if (empty && !st.global.transparent) Text(stringResource(R.string.sample_tag), color = Color.White, fontSize = 11.5.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0x8C000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
        if (empty) {
            Column(Modifier.matchParentSize().padding(if (compact) 0.dp else 0.dp).clip(RoundedCornerShape(16.dp)).background(stageBg.copy(alpha = 0.68f))
                .border(2.dp, Color(0x47FFFFFF), RoundedCornerShape(16.dp)).clickable(onClick = onPick).padding(16.dp).testTag("add_files"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                if (!compact) Icon(OldIcons.Upload, null, Modifier.size(46.dp), tint = Color(0xFFE9EBF1))
                Text(stringResource(R.string.drop_title), color = Color(0xFFE9EBF1), style = MaterialTheme.typography.titleMedium, fontSize = 17.sp)
                if (!compact) Text(stringResource(R.string.drop_sub), color = Color(0xFFE9EBF1).copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center)
                Text(stringResource(R.string.drop_pick), color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp).background(BtnBlue, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 9.dp))
            }
        }
    }
}

@Composable
private fun Checker(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val c = 11.dp.toPx()
        drawRect(Color(0x66888888))
        var y = 0f; var r = 0
        while (y < size.height) {
            var x = if (r % 2 == 0) 0f else c
            while (x < size.width) { drawRect(Color(0x22FFFFFF), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(c, c)); x += 2 * c }
            y += c; r++
        }
    }
}

fun fmtClock(sec: Double): String {
    val m = (sec / 60).toInt(); val s = sec - m * 60
    return "%d:%04.1f".format(m, s)
}

@Composable
fun Transport(st: UiState, clock: State<Float>) {
    val cs = MaterialTheme.colorScheme
    val mode = st.editing.mode
    val isImage = st.currentItem == null || st.currentItem?.info?.kind == MediaKind.IMAGE || st.global.transparent
    val note = if (mode == CreditMode.BLINK) stringResource(if (isImage) R.string.tn_blink_img else R.string.tn_blink_vid)
        else stringResource(if (isImage) R.string.tn_static_img else R.string.tn_static_vid)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).border(1.dp, cs.outline, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Text(if (mode == CreditMode.BLINK) fmtClock(clock.value.toDouble()) else "0:00.0", color = cs.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 16.sp, modifier = Modifier.widthIn(min = 56.dp))
                BoxWithConstraints(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(9.dp)).background(cs.surfaceVariant)) {
                    val w = this.maxWidth
                    val bandStart = if (mode == CreditMode.BLINK) w / 3 else 0.dp
                    val bandW = if (mode == CreditMode.BLINK) w / 3 else w
                    Box(Modifier.offset(x = bandStart).width(bandW).fillMaxHeight().background(cs.primaryContainer)
                        .border(2.dp, cs.primary, androidx.compose.ui.graphics.RectangleShape))
                    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        for (t in listOf("0", "1s", "2s", "3s")) Text(t, fontSize = 10.sp, color = cs.onSurfaceVariant)
                    }
                    if (mode == CreditMode.BLINK) {
                        val f = (clock.value / 3f).coerceIn(0f, 1f)
                        Box(Modifier.offset(x = w * f - 2.dp * f).width(2.dp).fillMaxHeight().background(cs.onBackground))
                    }
                }
            }
        }
        Text(note, fontSize = 12.8.sp, color = cs.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ editor
@Composable
fun FilmStrip(st: UiState, vm: MainViewModel, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 6.dp, start = 2.dp, end = 2.dp)) {
        itemsIndexed(st.items, key = { _, it -> it.info.id }) { i, it ->
            val on = i == st.current
            Box(Modifier.width(188.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surface)
                    .border(if (on) 2.dp else 1.5.dp, if (on) cs.primary else cs.outline, RoundedCornerShape(16.dp))
                    .clickable { vm.select(i) }.padding(7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(11.dp)).background(cs.surfaceVariant)) {
                        it.thumb?.let { b -> Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    Column(Modifier.weight(1f)) {
                        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                            Text(it.info.name, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                        }
                        Text(fmtBytes(it.info.sizeBytes) + when (it.info.kind) { MediaKind.VIDEO -> stringResource(R.string.kind_video); MediaKind.ANIM_GIF, MediaKind.ANIM_WEBP -> stringResource(R.string.kind_gif); else -> "" },
                            fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (it.override != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(9.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.primary))
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-1).dp * 0 + 7.dp * -1, y = (-7).dp).size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(cs.surface).border(1.dp, cs.outline, androidx.compose.foundation.shape.CircleShape).clickable { vm.remove(i) }, contentAlignment = Alignment.Center) {
                    Text("✕", fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
            }
        }
        item {
            Box(Modifier.width(76.dp).height(66.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, cs.outline, RoundedCornerShape(16.dp)).clickable(onClick = onPick), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.add_tile), fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun EditorTab(st: UiState, vm: MainViewModel, onPick: () -> Unit, onExport: (Boolean) -> Unit, onShare: (List<ResultEntry>) -> Unit) {
    val clock = rememberLoopClock()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Pad), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Stage(st, vm, clock, false, onPick)
        Transport(st, clock)
        if (st.items.isNotEmpty()) FilmStrip(st, vm, onPick)
        Section("") {
            val note = when {
                st.currentItem == null -> stringResource(R.string.per_note_none)
                st.hasOverride -> stringResource(R.string.per_note_ov, st.currentItem.info.name)
                else -> stringResource(R.string.per_note_glob)
            }
            CheckRow(stringResource(R.string.per_card), note, st.hasOverride, enabled = st.currentItem != null, tag = "per_file") { vm.toggleOverride() }
        }
        if (st.busy) {
            Section("") {
                LinearProgressIndicator(progress = { st.progress }, Modifier.fillMaxWidth())
                Text(st.progressLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                OutlinedButton(onClick = vm::cancelExport, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
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
fun CreditTab(st: UiState, vm: MainViewModel, onPick: () -> Unit) {
    val s = st.editing
    val cs = MaterialTheme.colorScheme
    val clock = rememberLoopClock()
    val tr = st.global.transparent
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = Pad).padding(top = 4.dp, bottom = 8.dp)) { Stage(st, vm, clock, true, onPick) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Pad), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Section(stringResource(R.string.h_text)) {
                OutlinedTextField(value = s.text, onValueChange = { v -> vm.edit { it.copy(text = v.take(StampSettings.MAX_TEXT)) } },
                    modifier = Modifier.fillMaxWidth().testTag("credit_text"), singleLine = true, shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
            }
            Section("") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!tr) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubLabel(stringResource(R.string.sub_place))
                        CornerBox(s.corner) { c -> vm.edit { it.copy(corner = c, dx = 0f, dy = 0f) } }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SubLabel(stringResource(R.string.sub_color))
                            Segmented(listOf(true to stringResource(R.string.color_white), false to stringResource(R.string.color_black)), s.white) { v -> vm.edit { it.copy(white = v) } }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SubLabel(stringResource(R.string.sub_weight))
                            Segmented(listOf(false to stringResource(R.string.weight_regular), true to stringResource(R.string.weight_bold)), s.bold) { v -> vm.edit { it.copy(bold = v) } }
                        }
                    }
                }
            }
            if (!tr) Section("") {
                CheckRow(stringResource(R.string.move_t), stringResource(R.string.move_s), st.moveOn) { vm.setMove(it) }
                if (st.moveOn) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RepeatButton("▲") { vm.nudge(0f, -0.5f) }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RepeatButton("◀") { vm.nudge(-0.5f, 0f) }
                                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant).clickable { vm.resetMove() }, contentAlignment = Alignment.Center) {
                                        Icon(OldIcons.Reset, stringResource(R.string.move_reset), Modifier.size(20.dp), tint = cs.primary)
                                    }
                                    RepeatButton("▶") { vm.nudge(0.5f, 0f) }
                                }
                                RepeatButton("▼") { vm.nudge(0f, 0.5f) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.move_help), Modifier.weight(1f), fontSize = 12.8.sp, color = cs.onSurfaceVariant, lineHeight = 21.sp)
                            Text(stringResource(if (s.dx != 0f || s.dy != 0f) R.string.pos_moved else R.string.pos_corner), fontSize = 12.sp, color = cs.primary,
                                modifier = Modifier.background(cs.primaryContainer, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
            Section(stringResource(R.string.h_shape)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeCard(stringResource(R.string.pm_credit_t), stringResource(R.string.pm_credit_s), s.mode == CreditMode.STATIC, false, Modifier.weight(1f)) { vm.applyPreset(Preset.CREDIT) }
                    ModeCard(stringResource(R.string.pm_blink_t), stringResource(R.string.pm_blink_s), s.mode == CreditMode.BLINK, true, Modifier.weight(1f)) { vm.applyPreset(Preset.BLINK) }
                }
            }
            Section("") {
                if (!tr) OldSlider(stringResource(R.string.sl_size), s.widthPct.toFloat(), 5f, 70f, 1f) { v -> vm.edit { it.copy(widthPct = v.toInt()) } }
                if (!tr) OldSlider(stringResource(R.string.sl_margin), s.marginPct, 0f, 12f, 0.5f) { v -> vm.edit { it.copy(marginPct = v) } }
                OldSlider(stringResource(R.string.sl_opacity), s.opacity.toFloat(), 10f, 100f, 5f) { v -> vm.edit { it.copy(opacity = v.toInt()) } }
                OldSlider(stringResource(R.string.sl_shadow), s.shadow.toFloat(), 0f, 100f, 5f) { v -> vm.edit { it.copy(shadow = v.toInt()) } }
            }
            Section("") {
                CheckRow(stringResource(R.string.tr_t), stringResource(R.string.tr_s), tr, tag = "transparent") { v -> vm.edit { it.copy(transparent = v) } }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SubLabel(t: String) = Text(t, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

// ------------------------------------------------------------------ output
@Composable
fun OutputTab(st: UiState, vm: MainViewModel) {
    val s = st.global
    val cs = MaterialTheme.colorScheme
    val tr = s.transparent
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Pad), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Section(stringResource(R.string.out_format)) {
            val fmts = listOf(
                Triple(OutFormat.AUTO, stringResource(R.string.fmt_auto_t), stringResource(R.string.fmt_auto_sub)),
                Triple(OutFormat.GIF, "GIF", stringResource(R.string.fmt_gif_sub)),
                Triple(OutFormat.WEBM, "WebM", stringResource(R.string.fmt_webm_sub)),
                Triple(OutFormat.MP4, "MP4", stringResource(R.string.fmt_mp4_sub)),
                Triple(OutFormat.PNG, "PNG", stringResource(R.string.fmt_png_sub)),
                Triple(OutFormat.JPG, "JPG", stringResource(R.string.fmt_jpg_sub)),
            )
            for (row in fmts.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((f, t, sub) in row) FormatChip(t, sub, s.format == f, Modifier.weight(1f)) { vm.edit { it.copy(format = f) } }
            }
            val hint = when (s.format) {
                OutFormat.AUTO -> R.string.hint_auto; OutFormat.GIF -> R.string.hint_gif; OutFormat.WEBM -> R.string.hint_webm
                OutFormat.MP4 -> R.string.hint_mp4; OutFormat.PNG -> R.string.hint_png; else -> R.string.hint_jpg
            }
            Text(stringResource(hint), fontSize = 12.8.sp, color = cs.onSurfaceVariant, lineHeight = 21.sp)
        }
        Section(stringResource(R.string.h_size)) {
            Segmented(listOf(SizeSel.ORIG to stringResource(R.string.size_orig), SizeSel.W1080 to "1080", SizeSel.W720 to "720", SizeSel.W540 to "540"), s.size) { v -> vm.edit { it.copy(size = v) } }
        }
        if (st.items.any { it.info.kind == MediaKind.VIDEO } && !tr && !s.sticker)
            Section("") { CheckRow(stringResource(R.string.audio_t), stringResource(R.string.audio_s), s.keepAudio) { v -> vm.edit { it.copy(keepAudio = v) } } }
        Section("") {
            CheckRow(stringResource(R.string.stk_t), stringResource(R.string.stk_s), s.sticker) { v -> vm.edit { it.copy(sticker = v) } }
            CheckRow(stringResource(R.string.crop_t), stringResource(R.string.crop_s), s.stickerCrop) { v -> vm.edit { it.copy(stickerCrop = v) } }
            Text(stringResource(R.string.stk_hint), fontSize = 12.8.sp, color = cs.onSurfaceVariant, lineHeight = 21.sp)
        }
        Text(stringResource(R.string.out_note), fontSize = 13.sp, color = cs.onSurfaceVariant, lineHeight = 22.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant).border(1.dp, cs.outline, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp))
        Spacer(Modifier.height(8.dp))
    }
}

// ------------------------------------------------------------------ settings
@Composable
fun SettingsTab(st: UiState, vm: MainViewModel, onLink: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Pad), verticalArrangement = Arrangement.spacedBy(Gap)) {
        val u = st.update
        if (u is UpdateUi.Available || u is UpdateUi.Downloading || u is UpdateUi.Ready) {
            val ver = when (u) { is UpdateUi.Available -> u.info.versionName; is UpdateUi.Downloading -> u.info.versionName; is UpdateUi.Ready -> u.info.versionName; else -> "" }
            Section("") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.upd_card_t), style = MaterialTheme.typography.titleMedium)
                    Text(ver, fontSize = 12.8.sp, color = cs.onSurfaceVariant)
                }
                Button(onClick = { vm.openUpdateSheet(true) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BtnBlue, contentColor = Color.White)) { Text(stringResource(R.string.upd_card_btn)) }
            }
        }
        Section(stringResource(R.string.set_appearance)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeCard(stringResource(R.string.theme_dark), true, st.dark, Modifier.weight(1f)) { vm.setDark(true) }
                ThemeCard(stringResource(R.string.theme_light), false, !st.dark, Modifier.weight(1f)) { vm.setDark(false) }
            }
        }
        Section("") {
            val sub = when (u) { is UpdateUi.Checking -> stringResource(R.string.set_checking); is UpdateUi.UpToDate -> stringResource(R.string.set_uptodate); else -> stringResource(R.string.upd_state) + " " + st.version }
            Box(Modifier.testTag("check_update")) { OldListRow(stringResource(R.string.upd_check_t), sub) { vm.checkUpdate(true) } }
            HorizontalDivider(color = cs.outline)
            OldListRow(stringResource(R.string.set_reset_items), stringResource(R.string.set_reset_items_sub)) { vm.resetAllItems() }
            HorizontalDivider(color = cs.outline)
            OldListRow(stringResource(R.string.set_reset_defaults), stringResource(R.string.set_reset_defaults_sub)) { vm.resetDefaults() }
        }
        Section("") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(BtnBlue), contentAlignment = Alignment.Center) { Text("@", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                Column {
                    Text(stringResource(R.string.about_t), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(stringResource(R.string.set_stamp_sub), fontSize = 12.8.sp, color = cs.onSurfaceVariant, lineHeight = 21.sp)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(cs.surface).border(1.dp, cs.outline, androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .clickable(onClick = onLink).padding(horizontal = 18.dp, vertical = 12.dp).testTag("wa_link"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(OldIcons.Chat, null, Modifier.size(20.dp), tint = Color(0xFF1FA855))
                    Text(stringResource(R.string.wa_handle), fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
            Text(stringResource(R.string.wa_note), fontSize = 12.5.sp, color = cs.onSurfaceVariant)
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
