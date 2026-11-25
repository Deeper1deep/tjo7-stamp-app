package com.tjo7.stamp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tjo7.stamp.R

val Poppins = FontFamily(Font(R.font.poppins_regular, FontWeight.Normal), Font(R.font.poppins_bold, FontWeight.Bold))

object Tokens {
    val Accent = Color(0xFF7C8CFF)
    val Teal = Color(0xFF2DD4BF)
    val Danger = Color(0xFFFF6B7A)
    val Warn = Color(0xFFFFB84D)
    val Ok = Color(0xFF3DDC97)
}

private val Dark = darkColorScheme(
    primary = Tokens.Accent, onPrimary = Color(0xFF0B0F24), secondary = Tokens.Teal,
    background = Color(0xFF0B0F24), onBackground = Color(0xFFE8EBFF),
    surface = Color(0xFF141A38), onSurface = Color(0xFFE8EBFF),
    surfaceVariant = Color(0xFF1B2347), onSurfaceVariant = Color(0xFF9AA3D1),
    outline = Color(0xFF2C3566), error = Tokens.Danger,
)
private val Light = lightColorScheme(
    primary = Color(0xFF4A5BE0), onPrimary = Color.White, secondary = Color(0xFF0F9E8E),
    background = Color(0xFFF3F5FF), onBackground = Color(0xFF12183A),
    surface = Color.White, onSurface = Color(0xFF12183A),
    surfaceVariant = Color(0xFFE6E9FA), onSurfaceVariant = Color(0xFF5A628C),
    outline = Color(0xFFC9CEEA), error = Color(0xFFD93A4B),
)

private fun type(): Typography {
    val base = Typography()
    fun TextStyle.p() = copy(fontFamily = Poppins)
    return Typography(
        titleLarge = base.titleLarge.p().copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = base.titleMedium.p().copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        bodyLarge = base.bodyLarge.p(), bodyMedium = base.bodyMedium.p().copy(fontSize = 14.sp),
        bodySmall = base.bodySmall.p().copy(fontSize = 12.sp),
        labelLarge = base.labelLarge.p(), labelMedium = base.labelMedium.p(),
    )
}

@Composable
fun Tjo7Theme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = type(), content = content)
}
