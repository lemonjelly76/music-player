package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Rating
import io.github.lemonjelly76.musicplayer.data.serviceOfLink

/**
 * Albums kept for other activities, or in Mainstream, shown one at a time with one tap to say whether each suits this
 * activity too, so activities soon differ from one another.
 */
@Composable
fun SortScreen(session: SortSession, activity: String, viewModel: MainViewModel, open: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val entry = session.current
            if (entry == null) {
                Finished(session, activity, viewModel::stopSorting)
            } else {
                Text("${session.number} of ${session.albums.size}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { session.number.toFloat() / session.albums.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                AlbumToSort(entry, activity, open)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rating.entries.forEach { rating ->
                        SortButton(rating, Modifier.weight(1f)) { viewModel.sort(rating) }
                    }
                }
                TextButton(onClick = { viewModel.sort(null) }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Skip")
                }
            }
        }
    }
}

@Composable
private fun AlbumToSort(entry: LibraryAlbum, activity: String, open: (String) -> Unit) {
    val album = entry.album
    // Small enough that the rating buttons stay on screen on a phone without scrolling.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AlbumArt(album, Modifier.fillMaxWidth(0.7f).clickable(enabled = album.link != null) { album.link?.let(open) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Good for $activity?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(album.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        album.link?.let { link ->
            TextButton(onClick = { open(link) }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text("Listen in ${serviceOfLink(link)}")
            }
        }
    }
}

/** Love is the one filled button, as the answer people give most when sorting albums they already keep. */
@Composable
private fun SortButton(rating: Rating, modifier: Modifier, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(rating.icon, contentDescription = null, Modifier.size(20.dp))
            Text(rating.label, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
    val padding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    if (rating == Rating.LOVE) {
        Button(onClick, modifier, contentPadding = padding) { content() }
    } else {
        OutlinedButton(onClick, modifier, contentPadding = padding) { content() }
    }
}

@Composable
private fun Finished(session: SortSession, activity: String, done: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(AppIcons.MusicNote, contentDescription = null, Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text("All sorted", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${session.rated} of ${session.albums.size} rated for $activity.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = done) { Text("Done") }
    }
}

/** Home's prompt to sort what the person keeps elsewhere but has not rated for this activity. */
@Composable
fun SortCard(toSort: List<LibraryAlbum>, activity: String, start: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .clickable(onClick = start)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverMosaic(toSort.map { it.album }, Modifier.size(56.dp))
        Column(Modifier.weight(1f)) {
            Text("Sort ${toSort.size} albums for $activity", style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold)
            Text("Kept elsewhere, not rated for $activity yet", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = start) { Text("Start") }
    }
}
