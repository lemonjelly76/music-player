package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.PickList
import io.github.lemonjelly76.musicplayer.data.Playlist
import io.github.lemonjelly76.musicplayer.data.serviceOfLink
import io.ktor.http.encodeURLParameter

/**
 * Everything to put on for the chosen activity, as streaming apps lay out a home page: Claude's playlists of songs to
 * play straight through, the albums already loved or OK for it, what has been on repeat, and albums to try and rate.
 */
@Composable
fun HomeScreen(
    ui: UiState,
    viewModel: MainViewModel,
    open: (String) -> Unit,
    showList: (String) -> Unit,
    showPlaylist: (String) -> Unit,
    seeLibrary: () -> Unit,
) {
    val person = ui.person
    if (person == null) {
        Empty(if (ui.loading) "Loading…" else "Add someone through Claude first, e.g. \"Add me as Craig\".")
        return
    }
    val activity = ui.chosenCategory?.name
    val askForPicks = { open(askClaudeForPicks(person.name, activity)) }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionTitle("Playlists") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(ui.playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    PlaylistCard(playlist, newest = index == 0, show = { showPlaylist(playlist.id) }, open = open)
                }
                item {
                    AskClaudeCard("Make a playlist", "${Playlist.SIZE} songs for ${ui.activityName}") {
                        open(askClaudeForPlaylist(person.name, activity))
                    }
                }
            }
        }
        item { SectionTitle("Your albums for ${ui.activityName}", action = "See all".takeIf { ui.library.isNotEmpty() }, onAction = seeLibrary) }
        item {
            if (ui.library.isEmpty()) {
                Hint("Nothing loved for ${ui.activityName} yet. Rate the albums to try below, or tell Claude what you love.")
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(ui.library.take(ALBUMS_ON_HOME), key = { it.id }) { entry ->
                        AlbumTile(entry, viewModel, open, Modifier.width(140.dp), details = false)
                    }
                }
            }
        }
        ui.onRepeat?.albums?.takeIf { it.isNotEmpty() }?.let { played ->
            val title = if (ui.chosenCategory == null) "On repeat" else "On repeat for ${ui.activityName}"
            item { SectionTitle(title, action = "Your listening", onAction = viewModel::openListening) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(played.take(ALBUMS_ON_HOME)) { PlayedTile(it, open, Modifier.width(140.dp)) }
                }
            }
        }
        if (ui.toSort.isNotEmpty()) {
            item { Spacer(Modifier.height(16.dp)) }
            item { SortCard(ui.toSort, ui.activityName, viewModel::startSorting) }
        }
        item { SectionTitle("Albums to try", action = "Get more", onAction = askForPicks) }
        val latest = ui.latest
        if (latest == null) {
            item { Hint("Ask Claude for albums to try for ${ui.activityName}; they show here to open and rate.") }
        } else {
            items(latest.picks, key = { "pick-${it.id}" }) { pick -> PickRow(pick, viewModel, open) }
        }
        val earlier = ui.lists.drop(1)
        if (earlier.isNotEmpty()) {
            item { SectionTitle("Earlier albums to try") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(earlier, key = { it.id }) { list -> AlbumListCard(list) { showList(list.id) } }
                }
            }
        }
    }
}

/** A playlist's card: its covers, and a play button on the newest, the one still in the person's account. */
@Composable
private fun PlaylistCard(playlist: Playlist, newest: Boolean, show: () -> Unit, open: (String) -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = show), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            CoverMosaic(playlist.covers, Modifier.size(150.dp))
            playlist.link?.let { link ->
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { open(link) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play in ${serviceOfLink(link)}",
                        tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Text(if (newest) "Latest playlist" else shortDate(playlist.createdAt), style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(playlist.description.ifBlank { songCount(playlist.tracks.size) }, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** An earlier list of albums to try, by date. */
@Composable
private fun AlbumListCard(list: PickList, show: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = show), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CoverMosaic(list.picks.map { it.album }, Modifier.size(150.dp))
        Text(shortDate(list.createdAt), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text("${list.picks.size} albums", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The shelf's last card: new playlists come from a conversation with Claude, so this starts one. */
@Composable
private fun AskClaudeCard(title: String, subtitle: String, ask: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = ask), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(150.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.AutoAwesome, contentDescription = null, Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        action?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Empty(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(AppIcons.MusicNote, contentDescription = null, Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val ALBUMS_ON_HOME = 20

/** A new conversation in Claude with the request written out; the person still sends it. */
fun askClaudeForPicks(person: String, activity: String?): String =
    askClaude("Give $person 10 new albums to try" + (activity?.let { " for $it" } ?: "") + ".")

fun askClaudeForPlaylist(person: String, activity: String?): String =
    askClaude("Make $person a ${Playlist.SIZE}-song playlist" + (activity?.let { " for $it" } ?: "") + ".")

private fun askClaude(request: String): String =
    "https://claude.ai/new?q=" + request.encodeURLParameter(spaceToPlus = true)

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-10-20T18:00:00Z" reads "20 Oct 2026"; anything else is shown as it came. */
fun shortDate(iso: String): String {
    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso) ?: return iso
    val (year, month, day) = match.destructured
    val name = MONTHS.getOrNull(month.toInt() - 1) ?: return iso
    return "${day.toInt()} $name $year"
}
