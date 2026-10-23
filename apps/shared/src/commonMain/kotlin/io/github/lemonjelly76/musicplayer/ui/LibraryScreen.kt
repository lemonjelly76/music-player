package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Rating
import io.github.lemonjelly76.musicplayer.data.serviceOfLink

/**
 * The person's albums for the activity as a wall of covers, narrowed to loved, OK, only this activity's own ratings,
 * or what they turned down; tapping one opens it in their streaming app.
 */
@Composable
fun LibraryScreen(ui: UiState, viewModel: MainViewModel, open: (String) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(MusicKind.ALBUMS) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    val anything = if (kind == MusicKind.ALBUMS) ui.library.isNotEmpty() || ui.disliked.isNotEmpty() else ui.songs.isNotEmpty()
    Column {
        KindPills(kind) { kind = it }
        if (anything) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LibraryFilter.entries) { option ->
                    Pill(option.label, null, selected = option == filter) { filter = option }
                }
            }
        }
        if (kind == MusicKind.SONGS) {
            val songs = filter.applyToSongs(ui.songs)
            if (songs.isEmpty()) Empty(emptySongsText(ui, filter))
            LazyColumn(Modifier.fillMaxWidth()) {
                items(songs, key = { it.ref }) { track ->
                    SongRow(track, ui.activityName, rate = { viewModel.rateSong(track, it) }, open = open)
                }
            }
            return@Column
        }
        val shown = filter.apply(ui.library + ui.disliked)
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

private fun emptySongsText(ui: UiState, filter: LibraryFilter): String = when (filter) {
    LibraryFilter.ALL -> "No songs loved for ${ui.activityName} yet. Rate songs in a playlist or from Search, or " +
        "tell Claude which songs ${ui.person?.name ?: "you"} love."
    LibraryFilter.NOT_FOR_ME -> "No songs turned down for ${ui.activityName}. Claude leaves them out of its playlists."
    else -> "No songs rated ${filter.label} for ${ui.activityName}."
}

private fun emptyLibraryText(ui: UiState, filter: LibraryFilter): String = when (filter) {
    LibraryFilter.ALL -> "Nothing loved for ${ui.activityName} yet. Rate picks on Home, or tell Claude which albums " +
        "${ui.person?.name ?: "you"} love."
    LibraryFilter.NOT_FOR_ME -> "Nothing turned down for ${ui.activityName}. Albums rated Not for me show here, " +
        "so you can change your mind."
    else -> "No albums rated ${filter.label} for ${ui.activityName}."
}

/**
 * An album's cover, title, artist and rating, with a menu to open it, find more like it, rate it here or remove it;
 * holding it shows the same. [details] adds its notes and tags.
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
    val ratings = Rating.entries.reversed().filter { it != entry.rating }
        .map { rating -> MenuAction(rating.label) { viewModel.rate(album, rating) } } +
        listOfNotNull(MenuAction("Remove rating") { viewModel.rate(album, null) }.takeIf { entry.rating != null })
    val actions = listOfNotNull(
        link?.let { MenuAction("Open in ${serviceOfLink(it)}") { open(it) } },
        MenuAction("More like this") { viewModel.moreLikeThis(album) },
    ) + elsewhereActions(album, open) + ratings + MenuAction("Remove from your albums", Confirm("Remove ${album.title}?",
        "It leaves your albums, with its ratings for every activity, so Claude may suggest it again.", "Remove")) {
        viewModel.removeFromLibrary(entry)
    }
    var held by remember { mutableStateOf(false) }
    HeldActions(held, { held = false }, album.title, album.artist, actions) { AlbumArt(album, Modifier.fillMaxSize()) }
    Column(modifier.holdable("Show actions for ${album.title}", onHold = { held = true },
        onClick = link?.let { { open(it) } })) {
        AlbumArt(album, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                QualityBadges(album)
                entry.rating?.let { RatingLabel(it) }
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

/** Loved in green, OK and Not for me in grey. */
@Composable
fun RatingLabel(rating: Rating) {
    val colour = if (rating == Rating.LOVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(rating.icon, contentDescription = null, Modifier.size(14.dp), tint = colour)
        Text(rating.label, style = MaterialTheme.typography.bodySmall,
            color = colour)
    }
}
