package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Rating

/** What rating an artist does for the activity, as the sheet explains it. */
fun artistRatingMeaning(rating: Rating, activity: String): String = when (rating) {
    Rating.LOVE -> "Their albums, and artists like them, lead Claude's picks for $activity."
    Rating.OK -> "Noted for Claude; it doesn't change what is picked for $activity."
    Rating.DISLIKE -> "Nothing of theirs is picked or put in a playlist for $activity, even made with others."
}

/** "Rate Air", for the menus of albums and songs, opening the artist's rating sheet. */
fun rateArtistAction(artist: String, viewModel: MainViewModel): MenuAction =
    MenuAction("Rate $artist") { viewModel.openArtistRating(artist) }

/**
 * Love, OK or Not for me for an artist as a whole, for the chosen activity; tapping the chosen one again takes the
 * rating away. The rating's effect is spelled out, as turning an artist down reaches further than an album.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistRatingSheet(artist: String, ui: UiState, viewModel: MainViewModel) {
    val current = ui.artists.firstOrNull { it.isCalled(artist) }?.rating
    val activity = ui.activityName
    ModalBottomSheet(onDismissRequest = viewModel::closeArtistRating,
        containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Initial(artist, 48.dp)
                Column {
                    Text(artist, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("The artist, for $activity", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RatingPills(current) { viewModel.rateArtist(artist, it) }
            listOf(Rating.LOVE, Rating.DISLIKE).forEach { rating ->
                Text("${rating.label}: ${artistRatingMeaning(rating, activity)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
