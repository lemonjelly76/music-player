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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Person
import io.github.lemonjelly76.musicplayer.data.Playlist
import io.github.lemonjelly76.musicplayer.data.Service
import io.github.lemonjelly76.musicplayer.data.serviceLabel

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
        copyService(ui.person, ui.services)?.let { other ->
            item { CopySwitch(playlist, ui, other, viewModel) }
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

/**
 * Whether the activity's newest playlist is also kept on [other], under the activity's own name, as for Google Home
 * on Spotify while listening on Tidal. That service has to be connected first.
 */
@Composable
private fun CopySwitch(playlist: Playlist, ui: UiState, other: String, viewModel: MainViewModel) {
    val service = serviceLabel(other)
    val connected = other in ui.person?.alsoConnected.orEmpty()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Also in $service", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(if (connected) "Keeps ${ui.activityName}'s newest playlist there as ${playlist.name}, for voice " +
                "assistants and play counts." else "Connect $service to keep ${ui.activityName}'s newest playlist " +
                "there too.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (connected) {
            Switch(checked = ui.copy?.service == other, onCheckedChange = { on ->
                viewModel.copyPlaylists(if (on) other else null)
            })
        } else {
            Pill("Connect", null, selected = false) { viewModel.connect(other) }
        }
    }
}

/** The service a person's playlists can be copied to: the other one the server is set up for. */
fun copyService(person: Person?, services: List<Service>): String? {
    val own = person?.service ?: return null
    return services.firstOrNull { it.configured && it.name != own }?.name
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
