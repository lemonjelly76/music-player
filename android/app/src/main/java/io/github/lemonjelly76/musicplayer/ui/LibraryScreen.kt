package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LibraryScreen(ui: UiState, viewModel: MainViewModel, open: (String) -> Unit) {
    if (ui.library.isEmpty()) {
        Empty("Nothing here yet. Tell Claude which albums ${ui.person?.name ?: "you"} love, or ask it to import favourites.")
        return
    }
    LazyColumn {
        items(ui.library, key = { it.id }) { entry ->
            AlbumCard(entry.album) {
                entry.rating?.let { Text("★".repeat(it) + "☆".repeat(5 - it), color = MaterialTheme.colorScheme.primary) }
                if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodyMedium)
                if (entry.tags.isNotEmpty()) {
                    Text(entry.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    entry.album.link?.let { link ->
                        TextButton(onClick = { open(link) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Text("Open in ${serviceName(link)}", Modifier.padding(start = 6.dp))
                        }
                    }
                    TextButton(onClick = { viewModel.moreLikeThis(entry.album) }) { Text("More like this") }
                }
            }
        }
    }
}
