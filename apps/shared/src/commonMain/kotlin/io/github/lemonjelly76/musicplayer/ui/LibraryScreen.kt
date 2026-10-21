package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Rating
import io.github.lemonjelly76.musicplayer.data.serviceOfLink

/** The person's albums as a wall of covers; tapping one opens it in their streaming app. */
@Composable
fun LibraryScreen(ui: UiState, viewModel: MainViewModel, open: (String) -> Unit) {
    if (ui.library.isEmpty()) {
        val activity = ui.chosenCategory?.name ?: "General"
        Empty("Nothing loved for $activity yet. Rate picks here, or tell Claude which albums " +
            "${ui.person?.name ?: "you"} love.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(ui.library, key = { it.id }) { entry -> LibraryTile(entry, viewModel, open) }
    }
}

@Composable
private fun LibraryTile(entry: LibraryAlbum, viewModel: MainViewModel, open: (String) -> Unit) {
    val album = entry.album
    val link = album.link
    val ratings = Rating.entries.reversed().filter { it != entry.rating || entry.general }
        .map { rating -> AlbumAction(rating.label) { viewModel.rate(album, rating) } }
    val actions = listOfNotNull(
        link?.let { AlbumAction("Open in ${serviceOfLink(it)}") { open(it) } },
        AlbumAction("More like this") { viewModel.moreLikeThis(album) },
    ) + ratings
    Column(Modifier.clickable(enabled = link != null) { link?.let(open) }) {
        AlbumArt(album, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                entry.rating?.let { RatingLabel(it, entry.general) }
                val details = listOf(entry.notes, entry.tags.joinToString(" · ")).filter { it.isNotBlank() }
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" — "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            ActionsMenu(actions)
        }
    }
}

/** Loved in green, OK in grey; "from General" when no rating was given for this activity itself. */
@Composable
private fun RatingLabel(rating: Rating, general: Boolean) {
    val colour = if (rating == Rating.LOVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(rating.icon, contentDescription = null, Modifier.size(14.dp), tint = colour)
        Text(if (general) "${rating.label} · from General" else rating.label, style = MaterialTheme.typography.bodySmall,
            color = colour)
    }
}
