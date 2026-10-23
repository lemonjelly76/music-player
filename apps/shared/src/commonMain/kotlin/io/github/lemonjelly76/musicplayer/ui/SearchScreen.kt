package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Found
import io.github.lemonjelly76.musicplayer.data.serviceOfLink
import kotlinx.coroutines.delay

/**
 * Finds albums or songs in the person's streaming catalogue so ones they already know can be rated for the activity;
 * Claude then has more to go on for picks and playlists. It searches as they type, once they pause.
 */
@Composable
fun SearchScreen(ui: UiState, viewModel: MainViewModel, open: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(ui.search.query) }
    val focus = LocalFocusManager.current
    LaunchedEffect(text) {
        if (text.trim() == viewModel.ui.value.search.query) return@LaunchedEffect
        delay(SEARCH_PAUSE_MILLIS)
        viewModel.search(text)
    }
    Column {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text(if (ui.search.kind == MusicKind.ALBUMS) "Albums or artists" else "Songs or artists") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { text = ""; viewModel.search("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(text); focus.clearFocus() }),
        )
        KindPills(ui.search.kind, viewModel::chooseSearchKind)
        val search = ui.search
        val whoLoves = ui.person?.name?.let { "$it already loves" } ?: "you already love"
        when {
            search.query.isEmpty() -> Empty(when (search.kind) {
                MusicKind.ALBUMS -> "Find albums $whoLoves and rate them for ${ui.activityName}. " +
                    "Claude uses them to pick new ones."
                MusicKind.SONGS -> "Find songs and rate them for ${ui.activityName}. Claude makes playlists " +
                    "from the ones ${ui.person?.name ?: "you"} love and leaves out the ones that aren't for them."
            })
            !search.answered -> Unit
            search.kind == MusicKind.ALBUMS && search.results.orEmpty().isNotEmpty() -> LazyColumn(Modifier.fillMaxWidth()) {
                items(search.results.orEmpty(), key = { it.album.ref }) { found ->
                    FoundRow(found, ui.activityName, viewModel, open)
                }
            }
            search.kind == MusicKind.SONGS && search.songs.orEmpty().isNotEmpty() -> LazyColumn(Modifier.fillMaxWidth()) {
                items(search.songs.orEmpty(), key = { it.ref }) { track ->
                    SongRow(track, ui.activityName, rate = { viewModel.rateSong(track, it) }, open = open)
                }
            }
            else -> Empty("Nothing found for \"${search.query}\".")
        }
    }
}

/** A search result with pills to rate it for the activity; a General rating standing in is named beneath. */
@Composable
private fun FoundRow(found: Found, activity: String, viewModel: MainViewModel, open: (String) -> Unit) {
    val album = found.album
    val actions = listOfNotNull(
        album.link?.let { link -> MenuAction("Open in ${serviceOfLink(link)}") { open(link) } },
        MenuAction("More like this") { viewModel.moreLikeThis(album) },
    )
    AlbumRow(album, open, actions) {
        val general = found.rating?.takeIf { found.general }
        general?.let {
            Text("${it.label} in General; rate it for $activity too", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RatingPills(found.rating.takeIf { !found.general }) { viewModel.rate(album, it) }
    }
}

/** Albums or songs, as pills, for Search and Library. */
@Composable
fun KindPills(chosen: MusicKind, choose: (MusicKind) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MusicKind.entries.forEach { kind -> Pill(kind.label, null, selected = kind == chosen) { choose(kind) } }
    }
}

private const val SEARCH_PAUSE_MILLIS = 500L
