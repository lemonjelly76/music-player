package io.github.lemonjelly76.musicplayer.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.lemonjelly76.musicplayer.ui.MainViewModel
import io.github.lemonjelly76.musicplayer.ui.MusicPlayerApp
import io.github.lemonjelly76.musicplayer.ui.MusicPlayerTheme
import java.awt.Desktop
import java.awt.Dimension
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.JOptionPane

fun main() = application {
    val viewModel = remember { MainViewModel(PreferencesSettingsStore()) }
    val icon = remember { BitmapPainter(ImageIO.read(object {}.javaClass.getResource("/icon.png")).toComposeImageBitmap()) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "AI Picks",
        icon = icon,
        state = rememberWindowState(width = 1100.dp, height = 760.dp),
    ) {
        window.minimumSize = Dimension(380, 560)
        MusicPlayerTheme {
            MusicPlayerApp(viewModel, open = ::open)
        }
    }
}

/** Tidal and Spotify links open in their desktop apps when installed, else in the browser. */
private fun open(url: String) {
    try {
        Desktop.getDesktop().browse(URI(url))
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(null, "Nothing on this computer can open $url", "AI Picks", JOptionPane.WARNING_MESSAGE)
    }
}
