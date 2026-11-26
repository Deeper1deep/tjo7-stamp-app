package com.tjo7.stamp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.tjo7.stamp.domain.Corner
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay

val Gap = 12.dp
val Pad = 16.dp

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat())
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, compact: Boolean = false, dot: ((T) -> androidx.compose.ui.graphics.Color)? = null, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for ((v, label) in options) {
            val on = v == selected
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(if (on) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                .then(if (on) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)) else Modifier)
                .clickable(role = Role.RadioButton) { onSelect(v) }.padding(vertical = if (compact) 5.dp else 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dot != null) Box(Modifier.size(11.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dot(v)).border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape))
                Text(label, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, hint: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OldToggle(checked)
    }
}

/** A button that repeats while it is held (used for the move-credit arrows). */
@Composable
fun RepeatButton(label: String, size: Dp = 44.dp, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) { step(); delay(350); while (true) { step(); delay(70) } }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; try { tryAwaitRelease() } finally { pressed = false } }) },
        contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun Pill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

fun fmtBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1048576 -> "%.0f KB".format(n / 1024.0)
    else -> "%.1f MB".format(n / 1048576.0)
}

@Composable
fun FormatChip(title: String, sub: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(14.dp))
        .background(if (on) cs.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
        .border(1.5.dp, if (on) cs.primary else cs.outline, RoundedCornerShape(14.dp))
        .clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (on) cs.primary else cs.onSurface)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
fun ThemeCard(label: String, dark: Boolean, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = if (dark) androidx.compose.ui.graphics.Color(0xFF05060D) else androidx.compose.ui.graphics.Color.White
    val bar = if (dark) androidx.compose.ui.graphics.Color(0xFF8FA3FF) else androidx.compose.ui.graphics.Color(0xFF2A47E8)
    Column(modifier.clip(RoundedCornerShape(16.dp))
        .background(if (on) cs.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
        .border(1.5.dp, if (on) cs.primary else cs.outline, RoundedCornerShape(16.dp))
        .clickable(role = Role.RadioButton, onClick = onClick).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(7.dp).clip(RoundedCornerShape(3.dp)).background(bar))
            Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(3.dp)).background(bar.copy(alpha = 0.35f)))
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ListRow(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AppHeader(dark: Boolean, onToggleTheme: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(BtnBlue), contentAlignment = Alignment.Center) {
                Text("@", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Column {
                Text(androidx.compose.ui.res.stringResource(com.tjo7.stamp.R.string.app_title), style = MaterialTheme.typography.titleMedium)
                Text(androidx.compose.ui.res.stringResource(com.tjo7.stamp.R.string.app_sub), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(14.dp)).testTag("theme_toggle").clickable(onClick = onToggleTheme), contentAlignment = Alignment.Center) {
            Text(if (dark) "☀" else "☾", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SaveBar(title: String, sub: String, label: String, enabled: Boolean, busy: Boolean, onSave: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.background).border(1.dp, cs.outline).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 16.sp)
        }
        Button(onClick = onSave, enabled = enabled && !busy, shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BtnBlue, contentColor = androidx.compose.ui.graphics.Color.White),
            contentPadding = PaddingValues(horizontal = 20.dp), modifier = Modifier.height(48.dp).testTag("save_btn")) {
            Icon(OldIcons.Download, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Tab heading used by the Credit, Output and Settings screens: title and subtitle at the start, optional action at the end. */
@Composable
fun TabTitle(title: String, sub: String? = null, action: (@Composable () -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (sub != null) Text(sub, fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
        if (action != null) action()
    }
}

@Composable
fun IconTile(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(18.dp), tint = cs.primary)
    }
}

@Composable
fun InfoChip(text: String) {
    val cs = MaterialTheme.colorScheme
    Text(text, fontSize = 10.5.sp, color = cs.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(cs.primaryContainer).padding(horizontal = 10.dp, vertical = 5.dp))
}

// ------------------------------------------------------------------ old-UI pieces

/** Line icons copied from the old web build's inline SVGs (24x24, 2px round strokes). */
object OldIcons {
    private fun build(name: String, vararg d: String): androidx.compose.ui.graphics.vector.ImageVector {
        val b = androidx.compose.ui.graphics.vector.ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in d) b.addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes(p), fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        return b.build()
    }
    val Editor by lazy { build("editor", "M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z", "M9 8.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2z", "M4 18l5-5 4 4 3-3 4 4") }
    val Credit by lazy { build("credit", "M12 8.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z", "M15.5 12v1.5a2.5 2.5 0 0 0 5 0V12a8.5 8.5 0 1 0-3.4 6.8") }
    val Output by lazy { build("output", "M4 7h10M18 7h2M4 17h2M10 17h10", "M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M8 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z") }
    val Settings by lazy { build("settings", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1") }
    val Upload by lazy { build("upload", "M12 16V4M7 9l5-5 5 5", "M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3") }
    val Chat by lazy { build("chat", "M21 11.5a8.4 8.4 0 0 1-12.4 7.4L3 20.5l1.7-5.4A8.4 8.4 0 1 1 21 11.5z") }
    val Chevron by lazy { build("chevron", "M15 6l-6 6 6 6") }
    val Reset by lazy { build("reset", "M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5") }
    val Download by lazy { build("download", "M12 4v11M7 11l5 5 5-5", "M5 20h14") }
    val Pause by lazy { build("pause", "M8 5v14M16 5v14") }
    val Plus by lazy { build("plus", "M12 5v14M5 12h14") }
    val Speaker by lazy { build("speaker", "M4 10v4h4l5 4V6l-5 4z", "M16.5 9a4 4 0 0 1 0 6") }
    val Sticker by lazy { build("sticker", "M6 4h12a2 2 0 0 1 2 2v8l-6 6H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M20 14h-4a2 2 0 0 0-2 2v4") }
    val Shield by lazy { build("shield", "M12 3l8 3v6c0 4.5-3.2 8-8 9-4.8-1-8-4.5-8-9V6z", "M9 12l2 2 4-4") }
}

/** Slider with the old app's exact range/step; the value text shows on the other side of the label. */
@Composable
fun OldSlider(label: String, value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit) {
    val shown = if (step < 1f) (if (value % 1f == 0f) "%d%%".format(value.toInt()) else "%.1f%%".format(value)) else "%d%%".format(value.toInt())
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(shown, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            val steps = (((max - min) / step).toInt() - 1).coerceAtLeast(0)
            ThinSlider(value = value.coerceIn(min, max), onValueChange = { onChange((Math.round((it - min) / step) * step + min).coerceIn(min, max)) },
                min = min, max = max)
        }
    }
}

/** Little box with a pill in each corner; the pressed corner is lit. */
@Composable
fun CornerBox(selected: Corner, onSelect: (Corner) -> Unit) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Box(Modifier.width(130.dp).height(78.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant)
            .border(1.dp, cs.outline, RoundedCornerShape(12.dp)).testTag("corner_box")) {
            for ((c, al) in listOf(Corner.TL to Alignment.TopStart, Corner.TR to Alignment.TopEnd, Corner.BL to Alignment.BottomStart, Corner.BR to Alignment.BottomEnd)) {
                Box(Modifier.align(al).size(65.dp, 39.dp).clickable(role = Role.RadioButton) { onSelect(c) }.padding(8.dp), contentAlignment = al) {
                    Box(Modifier.size(26.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(if (c == selected) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.45f)))
                }
            }
        }
    }
}

@Composable
fun ModeCard(title: String, sub: String, on: Boolean, blink: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(if (on) cs.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
        .border(1.5.dp, if (on) cs.primary else cs.outline, RoundedCornerShape(16.dp))
        .clickable(role = Role.RadioButton, onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val ink = if (on) cs.primary else cs.onSurfaceVariant
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(52.dp)) {
            val sw = 1.5.dp.toPx()
            if (!blink) {
                drawRoundRect(ink.copy(alpha = 0.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                drawRoundRect(ink, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.73f), size = androidx.compose.ui.geometry.Size(size.width * 0.22f, size.height * 0.12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            } else {
                val w = size.width * 0.3f
                for (i in 0..2) drawRoundRect(ink.copy(alpha = 0.4f), topLeft = androidx.compose.ui.geometry.Offset(i * (w + size.width * 0.035f), 0f), size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                drawRoundRect(ink, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.5f - size.width * 0.07f, size.height * 0.69f), size = androidx.compose.ui.geometry.Size(size.width * 0.14f, size.height * 0.12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (on) cs.primary else cs.onSurface)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

/** Toggle row like the old "chk" rows: text on the start, switch at the end, small grey hint under the title. */
@Composable
fun CheckRow(title: String, hint: String, checked: Boolean, enabled: Boolean = true, tag: String = "", onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
        .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OldToggle(checked, enabled)
    }
}

@Composable
fun OldListRow(title: String, sub: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        androidx.compose.material3.Icon(OldIcons.Chevron, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
    }
}


/** On/off switch drawn like the old app's: blue track + white knob at the end when on, grey track + white knob at the start when off. No animation. */
@Composable
fun OldToggle(on: Boolean, enabled: Boolean = true) {
    val track = if (on) BtnBlue else androidx.compose.ui.graphics.Color(0xFF3A3F55)
    val edge = if (on) BtnBlue else androidx.compose.ui.graphics.Color(0xFF5A6080)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Box(Modifier.size(width = 44.dp, height = 26.dp).alpha(if (enabled) 1f else 0.6f)) {
            Box(Modifier.matchParentSize().background(track, androidx.compose.foundation.shape.CircleShape).border(1.dp, edge, androidx.compose.foundation.shape.CircleShape))
            Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(4.dp).size(18.dp)
                .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.CircleShape))
        }
    }
}

/** Thin slider: 4dp track, 16dp white knob. Tap or drag anywhere on it. */
@Composable
fun ThinSlider(value: Float, onValueChange: (Float) -> Unit, min: Float = 0f, max: Float = 1f, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var wPx by remember { mutableStateOf(1f) }
    val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
    val set = rememberUpdatedState(onValueChange)
    Box(modifier.fillMaxWidth().height(24.dp).onSizeChanged { wPx = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(min, max) { detectTapGestures { o -> set.value(min + (o.x / wPx).coerceIn(0f, 1f) * (max - min)) } }
        .pointerInput(min, max) { detectHorizontalDragGestures { ch, _ -> ch.consume(); set.value(min + (ch.position.x / wPx).coerceIn(0f, 1f) * (max - min)) } },
        contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.primary.copy(alpha = 0.35f)))
        Box(Modifier.fillMaxWidth(frac).height(5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.primary))
        val d = androidx.compose.ui.platform.LocalDensity.current
        Box(Modifier.offset(x = with(d) { (frac * (wPx - 16.dp.toPx())).toDp() }).size(16.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.ui.graphics.Color.White))
    }
}
