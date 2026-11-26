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

// Old (web) design tokens, ported 1:1 from the original index.html :root / [data-theme=dark].
private val Dark = darkColorScheme(
    primary = Color(0xFF8FA3FF), onPrimary = Color(0xFF0A0F33), primaryContainer = Color(0xFF1D2759), onPrimaryContainer = Color(0xFF8FA3FF),
    secondary = Tokens.Teal,
    background = Color(0xFF0A0D1B), onBackground = Color(0xFFEEF1FF),
    surface = Color(0xFF141931), onSurface = Color(0xFFEEF1FF),
    surfaceVariant = Color(0xFF0F1428), onSurfaceVariant = Color(0xFFA3ACCB),
    outline = Color(0xFF262D4C), error = Tokens.Danger,
)
private val Light = lightColorScheme(
    primary = Color(0xFF2A47E8), onPrimary = Color.White, primaryContainer = Color(0xFFE3E8FF), onPrimaryContainer = Color(0xFF2A47E8),
    secondary = Color(0xFF0F9E8E),
    background = Color(0xFFF3F5FB), onBackground = Color(0xFF0E1330),
    surface = Color.White, onSurface = Color(0xFF0E1330),
    surfaceVariant = Color(0xFFEEF1F9), onSurfaceVariant = Color(0xFF5B6482),
    outline = Color(0xFFDDE2F0), error = Color(0xFFD93A4B),
)
/** The old app's button colour (#2a47e8) in both themes. */
val BtnBlue = Color(0xFF2A47E8)

private fun type(): Typography {
    val base = Typography()
    fun TextStyle.p() = copy(fontFamily = Poppins)
    return Typography(
        titleLarge = base.titleLarge.p().copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
        titleMedium = base.titleMedium.p().copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
        bodyLarge = base.bodyLarge.p().copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), bodyMedium = base.bodyMedium.p().copy(fontSize = 12.5.sp),
        bodySmall = base.bodySmall.p().copy(fontSize = 11.sp),
        labelLarge = base.labelLarge.p().copy(fontSize = 13.sp), labelMedium = base.labelMedium.p().copy(fontSize = 11.sp),
    )
}

@Composable
fun Tjo7Theme(dark: Boolean = true, content: @Composable () -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    // The old web UI's text ran about 12% larger than the first Compose port at the same screen width.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * 1.12f)) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = type(), content = content)
    }
}
