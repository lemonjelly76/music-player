package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.MAINSTREAM
import io.github.lemonjelly76.musicplayer.data.Rating

/** One of the activities a person's picks and library can be for, shown as a chip. */
data class Activity(val id: String, val name: String, val icon: ImageVector)

/**
 * Mainstream and each activity, scrolling sideways; every page follows the chosen one. Holding an activity offers to
 * rename or remove it; Mainstream stays as it is.
 */
@Composable
fun ActivityChips(activities: List<Activity>, chosen: String, choose: (String) -> Unit, hold: (Activity) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(activities, key = { it.id }) { activity ->
            ActivityChip(activity, selected = activity.id == chosen, choose = { choose(activity.id) },
                hold = { hold(activity) }.takeIf { activity.id != MAINSTREAM })
        }
    }
}

/** Drawn like [Pill], but it can also be held, which a Material chip cannot. */
@Composable
private fun ActivityChip(activity: Activity, selected: Boolean, choose: () -> Unit, hold: (() -> Unit)?) {
    val haptics = LocalHapticFeedback.current
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.minimumInteractiveComponentSize().height(32.dp).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = choose,
                onLongClickLabel = hold?.let { "Rename or remove ${activity.name}" },
                onLongClick = hold?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            )
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(activity.icon, contentDescription = null, Modifier.size(18.dp), tint = content)
        Text(activity.name, style = MaterialTheme.typography.labelLarge, color = content)
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
