package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Pick
import io.github.lemonjelly76.musicplayer.data.PickList
import io.github.lemonjelly76.musicplayer.data.serviceOfLink

@Composable
fun PicksScreen(ui: UiState, viewModel: MainViewModel, open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth()) {
        val list = ui.list
        if (list == null) {
            val scope = ui.chosenCategory?.name ?: "General"
            item {
                Empty(when {
                    ui.loading -> "Loading…"
                    ui.person == null -> "Add someone through Claude first, e.g. \"Add me as Craig\"."
                    else -> "No $scope picks for ${ui.person?.name} yet. Ask Claude for some."
                })
            }
            return@LazyColumn
        }
        item { ListHeader(list, ui.person?.name, open) }
        items(list.picks, key = { it.id }) { pick -> PickRow(pick, viewModel, open) }
    }
}

/**
 * The list as a playlist page: covers, name, who it is for, and a play button that opens it. A wide window puts the
 * covers beside the name, as desktop streaming apps do.
 */
@Composable
private fun ListHeader(list: PickList, personName: String?, open: (String) -> Unit) {
    val hue = (list.name.hashCode() and 0x7fffffff) % 360f
    val covers = list.picks.map { it.album }
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.hsl(hue, 0.35f, 0.28f),
                MaterialTheme.colorScheme.background)))
            .padding(16.dp),
    ) {
        if (maxWidth >= 600.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                    CoverMosaic(covers, Modifier.size(200.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(list.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        ListDetails(list, personName)
                    }
                }
                list.playlistLink?.let { PlayButton(it, open) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CoverMosaic(covers, Modifier.size(200.dp))
                }
                Text(list.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { ListDetails(list, personName) }
                    list.playlistLink?.let { PlayButton(it, open) }
                }
            }
        }
    }
}

@Composable
private fun ListDetails(list: PickList, personName: String?) {
    personName?.let {
        Text("Picked by Claude for $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    Text("${list.picks.size} albums · ${list.createdAt.take(10)}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PlayButton(link: String, open: (String) -> Unit) {
    FloatingActionButton(
        onClick = { open(link) },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play in ${serviceOfLink(link)}", Modifier.size(32.dp))
    }
}

@Composable
private fun PickRow(pick: Pick, viewModel: MainViewModel, open: (String) -> Unit) {
    val actions = listOfNotNull(
        pick.album.link?.let { link -> AlbumAction("Open in ${serviceOfLink(link)}") { open(link) } },
        AlbumAction("More like this") { viewModel.moreLikeThis(pick.album) },
    )
    AlbumRow(pick.album, open, actions, badge = if (pick.wildcard) "WILDCARD" else null) {
        Text(pick.reason, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        RatingPills(pick.rating) { viewModel.rate(pick.album, it) }
    }
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
