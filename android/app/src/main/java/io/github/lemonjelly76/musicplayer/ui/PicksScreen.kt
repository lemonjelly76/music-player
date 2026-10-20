package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Pick
import io.github.lemonjelly76.musicplayer.data.Verdict
import io.github.lemonjelly76.musicplayer.data.serviceOfLink

@Composable
fun PicksScreen(ui: UiState, viewModel: MainViewModel, open: (String) -> Unit) {
    val list = ui.list
    if (list == null) {
        val scope = ui.chosenCategory?.name?.lowercase() ?: "general"
        Empty(when {
            ui.loading -> "Loading…"
            ui.person == null -> "Add someone through Claude first, e.g. \"Add me as Craig\"."
            else -> "No $scope picks for ${ui.person?.name} yet. Ask Claude for some."
        })
        return
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(list.name, style = MaterialTheme.typography.titleLarge)
                    Text(list.createdAt.take(10), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                list.playlistLink?.let { link ->
                    FilledTonalButton(onClick = { open(link) }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                        Text("Play in ${serviceOfLink(link)}", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        items(list.picks, key = { it.id }) { pick -> PickCard(pick, viewModel, open) }
    }
}

@Composable
private fun PickCard(pick: Pick, viewModel: MainViewModel, open: (String) -> Unit) {
    AlbumCard(pick.album) {
        Text(pick.reason, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        if (pick.wildcard) {
            Text("Wildcard: Claude's own idea", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Verdict.entries.forEach { verdict ->
                FilterChip(
                    selected = pick.verdict == verdict,
                    onClick = { viewModel.feedback(pick.id, verdict) },
                    label = { Text(verdict.label) },
                )
            }
        }
        Row {
            pick.album.link?.let { link ->
                TextButton(onClick = { open(link) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Text("Open in ${serviceOfLink(link)}", Modifier.padding(start = 6.dp))
                }
            }
            TextButton(onClick = { viewModel.moreLikeThis(pick.album) }) { Text("More like this") }
        }
    }
}

@Composable
fun Empty(text: String) {
    Text(text, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
