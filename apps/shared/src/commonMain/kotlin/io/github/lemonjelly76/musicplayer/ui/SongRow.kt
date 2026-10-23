package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.Rating
import io.github.lemonjelly76.musicplayer.data.Track

/**
 * A song as streaming apps list one: an optional number, cover, title, quality badges, artist and album, and a button
 * to rate it for the activity or open it on another service. Tapping the row plays it; a song turned down is dimmed.
 */
@Composable
fun SongRow(track: Track, activity: String, rate: (Rating?) -> Unit, open: (String) -> Unit, number: Int? = null) {
    val link = track.link
    Row(
        Modifier.fillMaxWidth().clickable(enabled = link != null) { link?.let(open) }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).alpha(if (track.rating == Rating.DISLIKE) 0.45f else 1f),
            verticalAlignment = Alignment.CenterVertically) {
            number?.let {
                Text(it.toString(), Modifier.width(28.dp).padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            AlbumArt(Album(track.ref, track.artist, track.album, null, null, track.cover), Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    track.qualities.forEach { QualityBadge(it) }
                    Text(listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        SongRatingButton(track, activity, rate, open)
    }
}

/**
 * Shows the song's rating for the activity and opens the choices: Love, OK, Not for me, or taking the rating away;
 * then opening it on the person's other services.
 */
@Composable
private fun SongRatingButton(track: Track, activity: String, rate: (Rating?) -> Unit, open: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val own = track.rating
    val tint = when {
        own == null -> MaterialTheme.colorScheme.onSurfaceVariant
        track.rating == Rating.LOVE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val described = own?.label ?: "Not rated"
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(own?.icon ?: Icons.Outlined.FavoriteBorder, contentDescription = "$described; rate for $activity",
                tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Rate for $activity") },
                onClick = {}, enabled = false)
            Rating.entries.reversed().forEach { rating ->
                DropdownMenuItem(
                    text = { Text(rating.label) },
                    leadingIcon = { Icon(rating.icon, contentDescription = null) },
                    trailingIcon = if (own == rating) ({ Icon(Icons.Filled.Check, contentDescription = "Chosen") }) else null,
                    onClick = { expanded = false; if (own != rating) rate(rating) },
                )
            }
            if (own != null) {
                DropdownMenuItem(text = { Text("Remove rating") }, onClick = { expanded = false; rate(null) })
            }
            track.elsewhere.forEach { other ->
                DropdownMenuItem(text = { Text(other.label) }, onClick = { expanded = false; open(other.link) })
            }
        }
    }
}
