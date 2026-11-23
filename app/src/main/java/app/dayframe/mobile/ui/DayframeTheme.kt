package app.dayframe.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF090E1A)
val Panel = Color(0xFF121A2B)
val PanelRaised = Color(0xFF192338)
val OutlineSoft = Color(0xFF29344B)
val TextPrimary = Color(0xFFF1F3FF)
val TextMuted = Color(0xFF9BA6C0)
val Violet = Color(0xFF9488FF)
val VioletDeep = Color(0xFF655BCC)
val Mint = Color(0xFF78E0C2)
val Amber = Color(0xFFF0C36B)
val Coral = Color(0xFFFF817F)

private val DayframeColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    secondary = Mint,
    onSecondary = Ink,
    tertiary = Amber,
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextMuted,
    outline = OutlineSoft,
    error = Coral
)

@Composable
fun DayframeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DayframeColors,
        content = content
    )
}
