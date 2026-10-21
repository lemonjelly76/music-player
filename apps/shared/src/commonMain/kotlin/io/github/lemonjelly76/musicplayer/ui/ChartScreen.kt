package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.ChartAlbum
import io.github.lemonjelly76.musicplayer.data.Charts
import io.github.lemonjelly76.musicplayer.data.Rating
import io.github.lemonjelly76.musicplayer.data.serviceOfLink

/** "the UK", "the US"; the store code otherwise. */
fun chartPlace(country: String?): String = when (country?.lowercase()) {
    "gb" -> "the UK"
    "us" -> "the US"
    null -> "your country"
    else -> country.uppercase()
}

/**
 * The week's top 50 songs, to play straight through or one by one and rate, then the top albums; everything here is
 * rated for General, as the chart is not about any one activity.
 */
@Composable
fun ChartScreen(charts: Charts, viewModel: MainViewModel, open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader("Popular this week", charts.covers, charts.playlistLink, open) {
                Text("The ${charts.songs.size} most played songs in ${chartPlace(charts.country)}, from Apple " +
                    "Music's chart", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(listOfNotNull(charts.updatedAt?.let { "Updated ${shortDate(it)}" },
                    if (charts.playlistWanted) "In your streaming app every Monday" else "Weekly playlist off in Settings")
                    .joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(charts.songs, key = { "song-${it.position}" }) { entry ->
            SongRow(entry.song, "General", rate = { viewModel.rateSong(entry.song, it) }, open = open,
                number = entry.position)
        }
        if (charts.albums.isNotEmpty()) {
            item { SectionTitle("Top albums") }
            items(charts.albums, key = { "album-${it.position}" }) { entry ->
                val found = entry.album
                AlbumRow(found.album, open, albumActions(found.album, viewModel, open), badge = "#${entry.position}") {
                    RatingPills(found.rating) { viewModel.rate(found.album, it) }
                }
            }
        }
    }
}

private fun albumActions(album: Album, viewModel: MainViewModel, open: (String) -> Unit): List<AlbumAction> = listOfNotNull(
    album.link?.let { link -> AlbumAction("Open in ${serviceOfLink(link)}") { open(link) } },
    AlbumAction("More like this") { viewModel.moreLikeThis(album) },
)

/**
 * Home's chart shelf on General: the top 50 songs as a playlist card, then the top albums by chart place.
 */
@Composable
fun ChartShelf(charts: Charts, viewModel: MainViewModel, open: (String) -> Unit, showChart: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TopSongsCard(charts, showChart, open) }
        items(charts.albums.take(ALBUMS_ON_SHELF), key = { it.position }) { entry ->
            ChartAlbumTile(entry, viewModel, open)
        }
    }
}

@Composable
private fun TopSongsCard(charts: Charts, show: () -> Unit, open: (String) -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = show), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            CoverMosaic(charts.covers, Modifier.size(150.dp))
            charts.playlistLink?.let { link ->
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
        Text("Top ${charts.songs.size} songs", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text("Most played in ${chartPlace(charts.country)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A charting album: its cover, place, title and artist; the menu opens it, finds more like it, or rates it. */
@Composable
private fun ChartAlbumTile(entry: ChartAlbum, viewModel: MainViewModel, open: (String) -> Unit) {
    val found = entry.album
    val album = found.album
    val actions = albumActions(album, viewModel, open) + Rating.entries
        .reversed().filter { it != found.rating }
        .map { rating -> AlbumAction(rating.label) { viewModel.rate(album, rating) } }
    Column(Modifier.width(140.dp).clickable(enabled = album.link != null) { album.link?.let(open) },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            AlbumArt(album, Modifier.size(140.dp))
            Text("#${entry.position}", Modifier.padding(6.dp)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                found.rating?.let { RatingLabel(it, general = false) }
            }
            ActionsMenu(actions)
        }
    }
}

private const val ALBUMS_ON_SHELF = 20
