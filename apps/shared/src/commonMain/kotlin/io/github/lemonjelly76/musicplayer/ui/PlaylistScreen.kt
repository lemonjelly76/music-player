package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.Playlist
import io.github.lemonjelly76.musicplayer.data.Track

/** A playlist's page: its covers, mood and play button, then every song in play order. */
@Composable
fun PlaylistScreen(playlist: Playlist, personName: String?, open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            CollectionHeader(playlist.name, playlist.covers, playlist.link, open) {
                if (playlist.description.isNotBlank()) {
                    Text(playlist.description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                personName?.let {
                    Text("Made by Claude for $it", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                Text(playlistDetails(playlist), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(playlist.tracks) { index, track -> TrackRow(index + 1, track, open) }
    }
}

/** "50 songs · 21 Oct 2026", and whether it is still the one in the person's account. */
fun playlistDetails(playlist: Playlist): String =
    "${songCount(playlist.tracks.size)} · ${shortDate(playlist.createdAt)}" +
        (if (playlist.link == null) " · replaced since" else "")

fun songCount(songs: Int): String = if (songs == 1) "1 song" else "$songs songs"

/** A song as streaming apps list one: number, cover, title, artist and album; tapping it plays it. */
@Composable
private fun TrackRow(number: Int, track: Track, open: (String) -> Unit) {
    val link = track.link
    Row(
        Modifier.fillMaxWidth().clickable(enabled = link != null) { link?.let(open) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number.toString(), Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        AlbumArt(Album(track.id, track.artist, track.album, null, null, track.cover), Modifier.padding(start = 8.dp).size(48.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
