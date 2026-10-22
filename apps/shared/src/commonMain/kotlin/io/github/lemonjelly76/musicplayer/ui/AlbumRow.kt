package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import io.github.lemonjelly76.musicplayer.data.Album

/** The album's cover, or a tinted note when no catalogue had artwork or it would not load. */
@Composable
fun AlbumArt(album: Album, modifier: Modifier = Modifier, corner: Dp = 4.dp) {
    val shape = RoundedCornerShape(corner)
    SubcomposeAsyncImage(
        model = album.cover,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(1f).clip(shape),
        loading = { ArtPlaceholder(album) },
        error = { ArtPlaceholder(album) },
    )
}

/** A colour of its own for each album, so a wall of placeholders still tells albums apart. */
@Composable
private fun ArtPlaceholder(album: Album) {
    val hue = (album.title.hashCode() and 0x7fffffff) % 360f
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
            Color.hsl(hue, 0.45f, 0.35f), Color.hsl((hue + 40f) % 360f, 0.5f, 0.18f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
    }
}

/** Up to four covers in a square, as streaming apps show a playlist. */
@Composable
fun CoverMosaic(albums: List<Album>, modifier: Modifier = Modifier) {
    val covers = albums.take(4)
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp))) {
        if (covers.size < 4) {
            covers.firstOrNull()?.let { AlbumArt(it, Modifier.fillMaxSize(), corner = 0.dp) }
            return@Box
        }
        Column {
            covers.chunked(2).forEach { pair ->
                Row(Modifier.weight(1f)) {
                    pair.forEach { AlbumArt(it, Modifier.weight(1f), corner = 0.dp) }
                }
            }
        }
    }
}

/** Something to do with an album from its ⋯ menu. */
data class AlbumAction(val label: String, val run: () -> Unit)

/** "Open in Spotify" and the like, for the person's services other than their own. */
fun elsewhereActions(album: Album, open: (String) -> Unit): List<AlbumAction> =
    album.elsewhere.map { AlbumAction(it.label) { open(it.link) } }

/** The album's quality badges on a line of their own, for tiles too narrow to put them beside the artist. */
@Composable
fun QualityBadges(album: Album) {
    if (album.qualities.isNotEmpty()) {
        Row(Modifier.padding(top = 2.dp)) { album.qualities.forEach { QualityBadge(it) } }
    }
}

/**
 * A row with the album's cover, title and artist, then whatever the screen adds below. Tapping it opens the album in
 * the person's streaming app; its ⋯ menu also opens it on their other services.
 */
@Composable
fun AlbumRow(
    album: Album,
    open: (String) -> Unit,
    actions: List<AlbumAction> = emptyList(),
    badge: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val link = album.link
    val menu = actions + elsewhereActions(album, open)
    Column(
        Modifier.fillMaxWidth()
            .clickable(enabled = link != null) { link?.let(open) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(album, Modifier.size(56.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    badge?.let { Badge(it) }
                    album.qualities.forEach { QualityBadge(it) }
                    Text(listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            trailing()
            if (menu.isNotEmpty()) ActionsMenu(menu)
        }
        below()
    }
}

/** A small label before the artist, like the "E" streaming apps put on explicit tracks. */
@Composable
private fun Badge(text: String) {
    Text(text, Modifier.padding(end = 6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant,
        RoundedCornerShape(2.dp)).padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.background)
}

/** "Hi-Res" or "Atmos" in the accent colour, as Tidal marks its best-sounding releases. */
@Composable
fun QualityBadge(text: String) {
    Text(text, Modifier.padding(end = 6.dp).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        .padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun ActionsMenu(actions: List<AlbumAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, onClick = {
                    expanded = false
                    action.run()
                })
            }
        }
    }
}
