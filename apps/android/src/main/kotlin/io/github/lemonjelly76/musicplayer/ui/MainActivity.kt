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
import io.github.lemonjelly76.musicplayer.data.TIDAL_PACKAGE
import io.github.lemonjelly76.musicplayer.data.tidalAppLinks

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

    /**
     * Tidal links go straight to the Tidal app when it is installed, as Android would otherwise open them in the
     * browser; Spotify's open in its app by themselves. Anything else, or Tidal without its app, opens in the browser.
     */
    private fun open(url: String) {
        val inTidal = tidalAppLinks(url).any { link ->
            started(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(TIDAL_PACKAGE))
        }
        if (inTidal) return
        if (!started(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
            Toast.makeText(this, "Nothing on this phone can open $url", Toast.LENGTH_LONG).show()
        }
    }

    private fun started(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
