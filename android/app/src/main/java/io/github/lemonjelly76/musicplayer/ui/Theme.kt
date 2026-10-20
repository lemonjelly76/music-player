package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFE8A33D)

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Amber, secondary = Color(0xFFB8C4FF))
    } else {
        lightColorScheme(primary = Color(0xFF8A5A00), secondary = Color(0xFF3F51B5))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
