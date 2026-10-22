package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
        item {
            SlotPicker(playlist, ui.playlists, serviceName(ui.person?.service)) { viewModel.putInSlot(playlist, it) }
        }
        itemsIndexed(playlist.tracks) { index, track ->
            SongRow(track, ui.activityName, rate = { viewModel.rateSong(track, it) }, open = open, number = index + 1)
        }
    }
}

/**
 * Which of the activity's numbered playlists in the streaming app this one is, and the choice of slot: one already
 * there swaps numbers with the playlist in the chosen slot, one not there takes that playlist's place.
 */
@Composable
private fun SlotPicker(playlist: Playlist, playlists: List<Playlist>, service: String, choose: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(playlist.slot?.let { "In $service as ${playlist.name} $it" } ?: "Not in $service",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("$service keeps ${Playlist.SLOTS} of these playlists. Choose a number to put this one there" +
            (if (playlist.slot == null) ", replacing the one there now." else "; it swaps with the one there now."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            slotChoices(playlist, playlists).forEachIndexed { index, label ->
                val number = index + 1
                Pill(label, null, selected = playlist.slot == number) { if (playlist.slot != number) choose(number) }
            }
        }
    }
}

/** "spotify" reads "Spotify"; with no service, "your streaming app". */
fun serviceName(service: String?): String =
    service?.replaceFirstChar { it.uppercase() } ?: "your streaming app"

/** "50 songs · 21 Oct 2026". */
fun playlistDetails(playlist: Playlist): String =
    "${songCount(playlist.tracks.size)} · ${shortDate(playlist.createdAt)}"

/**
 * Each of the activity's slots in the streaming app with what holds it now, by its mood line, else its date:
 * "1 · Late-night slow burn", "2 · this one", "3 · empty".
 */
fun slotChoices(playlist: Playlist, playlists: List<Playlist>): List<String> = (1..Playlist.SLOTS).map { number ->
    val holder = playlists.firstOrNull { it.slot == number }
    "$number · " + when {
        holder == null -> "empty"
        holder.id == playlist.id -> "this one"
        holder.description.isNotBlank() -> holder.description.shortened(SLOT_LABEL_LENGTH)
        else -> shortDate(holder.createdAt).substringBeforeLast(' ')
    }
}

private fun String.shortened(length: Int): String =
    if (this.length <= length) this else take(length - 1).trimEnd() + "…"

private const val SLOT_LABEL_LENGTH = 22

fun songCount(songs: Int): String = if (songs == 1) "1 song" else "$songs songs"
