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
import io.github.lemonjelly76.musicplayer.data.serviceOfLink
import io.ktor.http.encodeURLParameter

/**
 * Everything to put on for the chosen activity, as streaming apps lay out a home page: the pick lists as playlists,
 * the albums already loved or OK for it, and the newest picks to try and rate.
 */
@Composable
fun HomeScreen(
    ui: UiState,
    viewModel: MainViewModel,
    open: (String) -> Unit,
    showList: (String) -> Unit,
    seeLibrary: () -> Unit,
) {
    val person = ui.person
    if (person == null) {
        Empty(if (ui.loading) "Loading…" else "Add someone through Claude first, e.g. \"Add me as Craig\".")
        return
    }
    val askClaude = { open(askClaudeForPicks(person.name, ui.chosenCategory?.name)) }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionTitle("Playlists") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(ui.lists, key = { it.id }) { list ->
                    PlaylistCard(list, newest = list == ui.latest, show = { showList(list.id) }, open = open)
                }
                item { AskClaudeCard(ui.activityName, askClaude) }
            }
        }
        item { SectionTitle("Your albums for ${ui.activityName}", action = "See all".takeIf { ui.library.isNotEmpty() }, onAction = seeLibrary) }
        item {
            if (ui.library.isEmpty()) {
                Hint("Nothing loved for ${ui.activityName} yet. Rate the picks below, or tell Claude what you love.")
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(ui.library.take(ALBUMS_ON_HOME), key = { it.id }) { entry ->
                        AlbumTile(entry, viewModel, open, Modifier.width(140.dp), details = false)
                    }
                }
            }
        }
        if (ui.toSort.isNotEmpty()) {
            item { Spacer(Modifier.height(16.dp)) }
            item { SortCard(ui.toSort, ui.activityName, viewModel::startSorting) }
        }
        ui.latest?.let { latest ->
            item { SectionTitle("New picks to try", action = "Get more", onAction = askClaude) }
            items(latest.picks, key = { "pick-${it.id}" }) { pick -> PickRow(pick, viewModel, open) }
        }
    }
}

/** A pick list as a playlist: its covers, and a play button on the newest, the one still in the person's account. */
@Composable
private fun PlaylistCard(list: PickList, newest: Boolean, show: () -> Unit, open: (String) -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = show), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            CoverMosaic(list.picks.map { it.album }, Modifier.size(150.dp))
            list.playlistLink?.let { link ->
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
        Text(if (newest) "Latest picks" else shortDate(list.createdAt), style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (newest) "${list.picks.size} albums · ${shortDate(list.createdAt)}" else "${list.picks.size} albums",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The shelf's last card: new picks come from a conversation with Claude, so this starts one. */
@Composable
private fun AskClaudeCard(activity: String, ask: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = ask), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(150.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.AutoAwesome, contentDescription = null, Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
        Text("Get new picks", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text("Ask Claude for $activity", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
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
fun askClaudeForPicks(person: String, activity: String?): String {
    val request = "Give $person 10 new album picks" + (activity?.let { " for $it" } ?: "") + "."
    return "https://claude.ai/new?q=" + request.encodeURLParameter(spaceToPlus = true)
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-10-20T18:00:00Z" reads "20 Oct 2026"; anything else is shown as it came. */
fun shortDate(iso: String): String {
    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso) ?: return iso
    val (year, month, day) = match.destructured
    val name = MONTHS.getOrNull(month.toInt() - 1) ?: return iso
    return "${day.toInt()} $name $year"
}
