package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The one accent: play buttons, chosen chips, ratings. */
val Accent = Color(0xFF1ED760)

private val Background = Color(0xFF121212)
private val Raised = Color(0xFF1F1F1F)
private val Highlight = Color(0xFF2A2A2A)
private val Muted = Color(0xFFB3B3B3)

/** Dark like a streaming app, so album artwork carries the colour. */
@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Accent,
        onPrimary = Color.Black,
        primaryContainer = Accent,
        onPrimaryContainer = Color.Black,
        secondary = Color.White,
        onSecondary = Color.Black,
        secondaryContainer = Highlight,
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFF7DD3FC),
        background = Background,
        onBackground = Color.White,
        surface = Background,
        onSurface = Color.White,
        surfaceVariant = Highlight,
        onSurfaceVariant = Muted,
        surfaceContainerLowest = Background,
        surfaceContainerLow = Raised,
        surfaceContainer = Raised,
        surfaceContainerHigh = Highlight,
        surfaceContainerHighest = Color(0xFF333333),
        outline = Color(0xFF727272),
        outlineVariant = Color(0xFF3E3E3E),
    )
    MaterialTheme(colorScheme = colors, content = content)
}
