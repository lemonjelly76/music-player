package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.serviceOfLink

/**
 * The top of a playlist or album list page, as streaming apps draw one: covers, name, details and, when it can be
 * played, a play button. A wide window puts the covers beside the name, as desktop streaming apps do.
 */
@Composable
fun CollectionHeader(
    name: String,
    covers: List<Album>,
    link: String?,
    open: (String) -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    val hue = (name.hashCode() and 0x7fffffff) % 360f
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.hsl(hue, 0.35f, 0.28f),
                MaterialTheme.colorScheme.background)))
            .padding(16.dp),
    ) {
        if (maxWidth >= 600.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                    CoverMosaic(covers, Modifier.size(200.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        details()
                    }
                }
                link?.let { PlayButton(it, open) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CoverMosaic(covers, Modifier.size(200.dp))
                }
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { details() }
                    link?.let { PlayButton(it, open) }
                }
            }
        }
    }
}

@Composable
private fun PlayButton(link: String, open: (String) -> Unit) {
    FloatingActionButton(
        onClick = { open(link) },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play in ${serviceOfLink(link)}", Modifier.size(32.dp))
    }
}
