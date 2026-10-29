package `in`.attend.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Monochrome base (white / black) with two small accents: dark purple for attendance, dark blue for planning.
// Green and red carry meaning only: on track or below the minimum.
val Ink = Color(0xFF111114)
val Amber = Color(0xFF9B601D)
private val Purple = Color(0xFF3B2A73)
private val Navy = Color(0xFF1F3A6B)
private val OnTrackLight = Color(0xFF2E7D4F)
private val BelowLight = Color(0xFFB3261E)
private val OnTrackDark = Color(0xFF7FD3A0)
private val BelowDark = Color(0xFFF2B8B5)

private val Light = lightColorScheme(
    primary = Ink, onPrimary = Color.White, primaryContainer = Color(0xFFF3F2F7), onPrimaryContainer = Ink,
    secondary = Purple, onSecondary = Color.White, secondaryContainer = Color(0xFFE9E4F7), onSecondaryContainer = Color(0xFF1D1540),
    tertiary = Navy, onTertiary = Color.White, tertiaryContainer = Color(0xFFE3EAF7), onTertiaryContainer = Color(0xFF0F1E3D),
    background = Color.White, surface = Color.White, onBackground = Ink, onSurface = Ink,
    surfaceContainer = Color(0xFFF4F4F6), surfaceVariant = Color(0xFFF4F4F6), onSurfaceVariant = Color(0xFF5C5C66),
    outline = Color(0xFF8A8A94), outlineVariant = Color(0xFFE2E2E8), error = BelowLight,
)
private val Dark = darkColorScheme(
    primary = Color(0xFFF2F2F5), onPrimary = Ink, primaryContainer = Color(0xFF1E1B2E), onPrimaryContainer = Color(0xFFECEAF6),
    secondary = Color(0xFFB9A9F2), onSecondary = Color(0xFF1D1540), secondaryContainer = Color(0xFF2A2052), onSecondaryContainer = Color(0xFFECEAF6),
    tertiary = Color(0xFF8FB0EA), onTertiary = Color(0xFF0F1E3D), tertiaryContainer = Color(0xFF1A2A4A), onTertiaryContainer = Color(0xFFDCE6FA),
    background = Color(0xFF0E0E10), surface = Color(0xFF0E0E10), onBackground = Color(0xFFEDEDF0), onSurface = Color(0xFFEDEDF0),
    surfaceContainer = Color(0xFF1A1A1E), surfaceVariant = Color(0xFF1A1A1E), onSurfaceVariant = Color(0xFFB4B4BE),
    outline = Color(0xFF6E6E78), outlineVariant = Color(0xFF2C2C33), error = BelowDark,
)

/** Status colours for "at or above the minimum" and "below it", matched to light or dark mode. */
@Composable fun onTrackColor() = if (isSystemInDarkTheme()) OnTrackDark else OnTrackLight
@Composable fun belowColor() = if (isSystemInDarkTheme()) BelowDark else BelowLight

@Composable
fun AttendTheme(content: @Composable () -> Unit) {
    val typography = Typography(
        headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium),
    )
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = typography, content = content)
}
