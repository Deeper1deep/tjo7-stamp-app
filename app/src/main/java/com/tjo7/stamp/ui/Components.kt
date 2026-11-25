package com.tjo7.stamp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay

val Gap = 12.dp
val Pad = 16.dp

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))) {
        Column(Modifier.padding(Pad), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
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
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for ((v, label) in options) {
            val on = v == selected
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(role = Role.RadioButton) { onSelect(v) }.padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
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
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A button that repeats while it is held (used for the move-credit arrows). */
@Composable
fun RepeatButton(label: String, size: Dp = 52.dp, onStep: () -> Unit) {
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
