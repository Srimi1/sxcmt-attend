package `in`.attend.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Forest = Color(0xFF315C48)
val Lime = Color(0xFFE3EDBD)
val Paper = Color(0xFFF8F7F2)
val Ink = Color(0xFF202C25)
val Amber = Color(0xFF9B601D)
private val Light = lightColorScheme(primary = Forest, onPrimary = Color.White, primaryContainer = Lime, onPrimaryContainer = Ink,
    secondary = Color(0xFF6A7650), secondaryContainer = Lime, onSecondaryContainer = Ink,
    surfaceContainer = Color(0xFFF0F1E8), background = Paper, surface = Paper, onSurface = Ink, onBackground = Ink,
    surfaceVariant = Color(0xFFEDEEE5), onSurfaceVariant = Color(0xFF5B655E), outlineVariant = Color(0xFFDDE0D4))
private val Dark = darkColorScheme(primary = Color(0xFFA8D2B4), onPrimary = Ink, primaryContainer = Forest, onPrimaryContainer = Lime,
    background = Color(0xFF131C17), surface = Color(0xFF131C17), surfaceVariant = Color(0xFF25332B), onSurface = Color(0xFFE4E9DD),
    onBackground = Color(0xFFE4E9DD), onSurfaceVariant = Color(0xFFBAC8BA))

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
