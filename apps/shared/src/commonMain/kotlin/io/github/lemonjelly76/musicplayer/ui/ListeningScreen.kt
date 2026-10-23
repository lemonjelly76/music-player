package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.ArtistPlays
import io.github.lemonjelly76.musicplayer.data.Listening
import io.github.lemonjelly76.musicplayer.data.PlayedAlbum
import io.github.lemonjelly76.musicplayer.data.serviceLabel
import io.github.lemonjelly76.musicplayer.data.serviceOfLink

/** How far back the listening page looks. */
enum class Period(val days: Int, val label: String) {
    MONTH(28, "4 weeks"),
    HALF_YEAR(182, "6 months"),
    YEAR(365, "1 year");

    val since: String get() = "the last $label"
}

/** The listening page's period, and what was played in it once that has loaded. */
data class ListeningPage(val period: Period = Period.MONTH, val stats: Listening? = null)

fun playCount(plays: Int): String = if (plays == 1) "1 play" else "$plays plays"

/**
 * What the person has played most, as streaming apps show top artists and albums: everything in General, or only
 * plays from an activity's pick playlists.
 */
@Composable
fun ListeningScreen(page: ListeningPage, ui: UiState, viewModel: MainViewModel, open: (String) -> Unit) {
    val stats = page.stats
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.entries.forEach { period ->
                    Pill(period.label, null, selected = period == page.period) { viewModel.choosePeriod(period) }
                }
            }
        }
        when {
            stats == null -> Unit
            !stats.tracking -> item {
                Empty("Play counting is off, so nothing is counted. It can be turned on in Settings.")
            }
            stats.tracks == 0 -> item { Empty(nothingPlayed(ui, page.period)) }
            else -> {
                item { Summary(stats, scope(ui, page.period)) }
                item { SectionTitle("Top artists") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(stats.artists) { artist -> ArtistCircle(artist, stats.albums) }
                    }
                }
                item { SectionTitle("Top albums") }
                itemsIndexed(stats.albums) { index, played -> RankedAlbum(index + 1, played, viewModel, open) }
            }
        }
    }
}

private fun scope(ui: UiState, period: Period): String =
    (if (ui.chosenCategory == null) "All your listening" else "From your ${ui.activityName} picks") + ", ${period.since}"

/** Why there is nothing yet, which differs by activity and by what the person's service shares. */
private fun nothingPlayed(ui: UiState, period: Period): String {
    val service = ui.person?.service
    return when {
        ui.chosenCategory != null -> "Nothing played from your ${ui.activityName} picks in ${period.since}. " +
            "Plays count for ${ui.activityName} when they come from its AI Picks playlist."
        service == "tidal" -> "Tidal doesn't share what you play, so nothing can be counted yet."
        else -> "Nothing played in ${period.since} yet. New plays are read from " +
            (service?.let(::serviceLabel) ?: "your streaming service") + " about once an hour."
    }
}

@Composable
private fun Summary(stats: Listening, scope: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Figure(stats.tracks, "tracks played")
            Figure(stats.albumCount, if (stats.albumCount == 1) "album" else "albums")
        }
        Text(scope, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Figure(value: Int, label: String) {
    Column {
        Text(value.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** There are no artist photos, so an artist wears the cover of their most-played album, in a circle. */
@Composable
private fun ArtistCircle(artist: ArtistPlays, albums: List<PlayedAlbum>) {
    val cover = albums.firstOrNull { it.album.artist.equals(artist.name, ignoreCase = true) }?.album
    Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (cover != null) AlbumArt(cover, Modifier.size(112.dp).clip(CircleShape)) else Initial(artist.name, 112.dp)
        Text(artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(playCount(artist.plays), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RankedAlbum(rank: Int, played: PlayedAlbum, viewModel: MainViewModel, open: (String) -> Unit) {
    val album = played.album
    val actions = listOfNotNull(
        album.link?.let { link -> MenuAction("Open in ${serviceOfLink(link)}") { open(link) } },
        MenuAction("More like this") { viewModel.moreLikeThis(album) },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(rank.toString(), Modifier.padding(start = 16.dp).width(24.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        Column(Modifier.weight(1f)) {
            AlbumRow(album, open, actions, trailing = {
                Text(playCount(played.plays), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            })
        }
    }
}

/** An album on Home's "On repeat" shelf: its cover, title and how often it was played. */
@Composable
fun PlayedTile(played: PlayedAlbum, open: (String) -> Unit, modifier: Modifier = Modifier) {
    val link = played.album.link
    Column(modifier.clickable(enabled = link != null) { link?.let(open) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AlbumArt(played.album, Modifier.fillMaxWidth())
        Text(played.album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${played.album.artist} · ${playCount(played.plays)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
