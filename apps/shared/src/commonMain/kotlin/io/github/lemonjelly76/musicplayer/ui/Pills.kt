package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.Rating

/** One of the activities a person's picks and library can be for, shown as a chip. */
data class Activity(val id: String, val name: String, val icon: ImageVector)

/** General and each activity, scrolling sideways; Picks and Library both follow the chosen one. */
@Composable
fun ActivityChips(activities: List<Activity>, chosen: String, choose: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(activities, key = { it.id }) { activity ->
            Pill(activity.name, activity.icon, selected = activity.id == chosen) { choose(activity.id) }
        }
    }
}

/** Not for me, OK or Love; tapping the chosen one again takes the rating away. */
@Composable
fun RatingPills(current: Rating?, rate: (Rating?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Rating.entries.reversed().forEach { rating ->
            Pill(rating.label, rating.icon, selected = current == rating) {
                rate(if (current == rating) null else rating)
            }
        }
    }
}

val Rating.icon: ImageVector
    get() = when (this) {
        Rating.LOVE -> Icons.Filled.Favorite
        Rating.OK -> Icons.Outlined.ThumbUp
        Rating.DISLIKE -> AppIcons.ThumbDownOutlined
    }

/** Green when chosen, grey otherwise, as streaming apps draw their filters. */
@Composable
fun Pill(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, Modifier.size(18.dp)) } },
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
