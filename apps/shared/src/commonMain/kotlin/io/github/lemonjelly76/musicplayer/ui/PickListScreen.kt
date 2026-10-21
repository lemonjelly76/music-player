package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.lemonjelly76.musicplayer.data.Pick
import io.github.lemonjelly76.musicplayer.data.PickList
import io.github.lemonjelly76.musicplayer.data.serviceOfLink

/** An earlier list of albums to try, from Home: each album with Claude's reason and rating pills. */
@Composable
fun PickListScreen(list: PickList, personName: String?, viewModel: MainViewModel, open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            CollectionHeader("Albums to try", list.picks.map { it.album }, link = null, open) {
                personName?.let {
                    Text("Picked by Claude for $it", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                Text("${list.picks.size} albums · ${shortDate(list.createdAt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(list.picks, key = { it.id }) { pick -> PickRow(pick, viewModel, open) }
    }
}

@Composable
fun PickRow(pick: Pick, viewModel: MainViewModel, open: (String) -> Unit) {
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
