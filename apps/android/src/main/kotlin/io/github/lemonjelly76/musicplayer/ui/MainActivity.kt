package io.github.lemonjelly76.musicplayer.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lemonjelly76.musicplayer.data.SharedPreferencesSettingsStore

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(SharedPreferencesSettingsStore(applicationContext)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, whatever the phone is set to, so the bars keep light icons.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            MusicPlayerTheme {
                MusicPlayerApp(viewModel, open = ::open)
            }
        }
    }

    /** Tidal and Spotify links open in their own apps when installed, else in the browser. */
    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Nothing on this phone can open $url", Toast.LENGTH_LONG).show()
        }
    }
}
