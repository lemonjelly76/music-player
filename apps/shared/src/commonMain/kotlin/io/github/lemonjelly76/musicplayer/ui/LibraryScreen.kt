package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.GENERAL
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Rating
import io.github.lemonjelly76.musicplayer.data.serviceOfLink

/**
 * The person's albums for the activity as a wall of covers, narrowed to loved, OK, only this activity's own ratings,
 * or what they turned down; tapping one opens it in their streaming app.
 */
@Composable
fun LibraryScreen(ui: UiState, viewModel: MainViewModel, open: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    val filters = LibraryFilter.entries.filter { it != LibraryFilter.THIS_ACTIVITY || ui.category != GENERAL }
    if (filter !in filters) filter = LibraryFilter.ALL
    val shown = filter.apply(ui.library + ui.disliked)
    Column {
        if (ui.library.isNotEmpty() || ui.disliked.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filters) { option ->
                    val label = if (option == LibraryFilter.THIS_ACTIVITY) "Rated for ${ui.activityName}" else option.label
                    Pill(label, null, selected = option == filter) { filter = option }
                }
            }
        }
        if (shown.isEmpty()) {
            Empty(emptyLibraryText(ui, filter))
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(shown, key = { it.id }) { entry -> AlbumTile(entry, viewModel, open) }
        }
    }
}

private fun emptyLibraryText(ui: UiState, filter: LibraryFilter): String = when (filter) {
    LibraryFilter.ALL -> "Nothing loved for ${ui.activityName} yet. Rate picks on Home, or tell Claude which albums " +
        "${ui.person?.name ?: "you"} love."
    LibraryFilter.THIS_ACTIVITY -> "No albums rated for ${ui.activityName} itself yet; these all come from General. " +
        "Use an album's menu to rate it for ${ui.activityName}."
    LibraryFilter.NOT_FOR_ME -> "Nothing turned down for ${ui.activityName}. Albums rated Not for me show here, " +
        "so you can change your mind."
    else -> "No albums rated ${filter.label} for ${ui.activityName}."
}

/**
 * An album's cover, title, artist and rating, with a menu to open it, find more like it or rate it here; [details]
 * adds its notes and tags.
 */
@Composable
fun AlbumTile(
    entry: LibraryAlbum,
    viewModel: MainViewModel,
    open: (String) -> Unit,
    modifier: Modifier = Modifier,
    details: Boolean = true,
) {
    val album = entry.album
    val link = album.link
    val ratings = Rating.entries.reversed().filter { it != entry.rating || entry.general }
        .map { rating -> AlbumAction(rating.label) { viewModel.rate(album, rating) } } +
        listOfNotNull(AlbumAction("Remove rating") { viewModel.rate(album, null) }.takeIf { !entry.general })
    val actions = listOfNotNull(
        link?.let { AlbumAction("Open in ${serviceOfLink(it)}") { open(it) } },
        AlbumAction("More like this") { viewModel.moreLikeThis(album) },
    ) + ratings
    Column(modifier.clickable(enabled = link != null) { link?.let(open) }) {
        AlbumArt(album, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                entry.rating?.let { RatingLabel(it, entry.general) }
                val notes = listOf(entry.notes, entry.tags.joinToString(" · ")).filter { details && it.isNotBlank() }
                if (notes.isNotEmpty()) {
                    Text(notes.joinToString(" — "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            ActionsMenu(actions)
        }
    }
}

/** Loved in green, OK and Not for me in grey; "in General" when no rating was given for this activity itself. */
@Composable
private fun RatingLabel(rating: Rating, general: Boolean) {
    val colour = if (rating == Rating.LOVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(rating.icon, contentDescription = null, Modifier.size(14.dp), tint = colour)
        Text(if (general) "${rating.label} in General" else rating.label, style = MaterialTheme.typography.bodySmall,
            color = colour)
    }
}
