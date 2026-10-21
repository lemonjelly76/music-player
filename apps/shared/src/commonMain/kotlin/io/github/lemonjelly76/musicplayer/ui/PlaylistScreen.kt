package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Playlist

/** A playlist's page: its covers, mood and play button, then every song in play order, each to rate. */
@Composable
fun PlaylistScreen(playlist: Playlist, ui: UiState, viewModel: MainViewModel, open: (String) -> Unit) {
    val personName = ui.person?.name
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
        itemsIndexed(playlist.tracks) { index, track ->
            SongRow(track, ui.activityName, rate = { viewModel.rateSong(track, it) }, open = open, number = index + 1)
        }
    }
}

/** "50 songs · 21 Oct 2026", and whether it is still the one in the person's account. */
fun playlistDetails(playlist: Playlist): String =
    "${songCount(playlist.tracks.size)} · ${shortDate(playlist.createdAt)}" +
        (if (playlist.link == null) " · replaced since" else "")

fun songCount(songs: Int): String = if (songs == 1) "1 song" else "$songs songs"
