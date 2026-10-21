package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Instant suggestions from the server's ranked pool; no model is asked (R15). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreLikeThisSheet(state: MoreLikeThis, viewModel: MainViewModel, open: (String) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = viewModel::closeMoreLikeThis,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text("More like ${state.seed.title}", Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        if (!state.loading && state.candidates.isEmpty()) Empty("Nothing new found for this one.")
        LazyColumn {
            items(state.candidates) { candidate ->
                AlbumRow(candidate.album, open, trailing = {
                    IconButton(onClick = { viewModel.love(candidate.album) }) {
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Love it",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }) {
                    Text("Suggested by ${candidate.sources.joinToString()}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
