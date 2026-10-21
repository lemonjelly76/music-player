package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** An activity chip that was held, and how far the person has got with it. */
data class HeldActivity(val activity: Activity, val step: Step) {
    enum class Step { CHOOSE, RENAME, REMOVE, REORDER }
}

/**
 * What holding an activity's chip offers, as streaming apps show a sheet of actions for a held item: rename it,
 * remove it, or reorder the activities, each affecting the whole household. [activities] are all but General, in
 * order.
 */
@Composable
fun ActivityActions(
    held: HeldActivity,
    activities: List<Activity>,
    change: (HeldActivity?) -> Unit,
    rename: (String) -> Unit,
    remove: () -> Unit,
    move: (id: String, by: Int) -> Unit,
) {
    val activity = held.activity
    when (held.step) {
        HeldActivity.Step.CHOOSE -> ActivitySheet(activity, close = { change(null) },
            rename = { change(held.copy(step = HeldActivity.Step.RENAME)) },
            remove = { change(held.copy(step = HeldActivity.Step.REMOVE)) },
            reorder = { change(held.copy(step = HeldActivity.Step.REORDER)) })
        HeldActivity.Step.REORDER -> ReorderSheet(activities, held = activity.id, move, close = { change(null) })
        HeldActivity.Step.RENAME -> RenameDialog(activity, close = { change(null) }, rename = { change(null); rename(it) })
        HeldActivity.Step.REMOVE -> RemoveDialog(activity, close = { change(null) }, remove = { change(null); remove() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivitySheet(activity: Activity, close: () -> Unit, rename: () -> Unit, remove: () -> Unit,
    reorder: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(activity.icon, contentDescription = null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Text(activity.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        SheetAction(Icons.Outlined.Edit, "Rename", MaterialTheme.colorScheme.onSurface, rename)
        SheetAction(Icons.AutoMirrored.Filled.List, "Reorder activities", MaterialTheme.colorScheme.onSurface, reorder)
        SheetAction(Icons.Outlined.Delete, "Remove", MaterialTheme.colorScheme.error, remove)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = color)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/**
 * Every activity with arrows to move it a place earlier or later; each move is saved at once and the chips follow.
 * The activity that was held is picked out, so it is easy to find.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderSheet(activities: List<Activity>, held: String, move: (String, Int) -> Unit, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Text("Reorder activities", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text("General always comes first. The order is the same for everyone.",
            Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn {
            itemsIndexed(activities, key = { _, activity -> activity.id }) { index, activity ->
                val color = if (activity.id == held) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(activity.icon, contentDescription = null, tint = color)
                    Text(activity.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = color)
                    IconButton(onClick = { move(activity.id, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${activity.name} earlier")
                    }
                    IconButton(onClick = { move(activity.id, 1) }, enabled = index < activities.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${activity.name} later")
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The name starts selected, so typing replaces it. */
@Composable
private fun RenameDialog(activity: Activity, close: () -> Unit, rename: (String) -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(activity.name, TextRange(0, activity.name.length))) }
    val focus = remember { FocusRequester() }
    val changed = name.text.isNotBlank() && name.text.trim() != activity.name
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Rename ${activity.name}") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                supportingText = { Text("Renamed for everyone, with its playlists.") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (changed) rename(name.text.trim()) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { rename(name.text.trim()) }, enabled = changed) { Text("Rename") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

/** Removing an activity cannot be undone and affects everyone, so it is confirmed first. */
@Composable
private fun RemoveDialog(activity: Activity, close: () -> Unit, remove: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Remove ${activity.name}?") },
        text = {
            Text("This removes it for everyone in the household, with the ratings, albums to try and playlists " +
                "for it. Albums rated only for ${activity.name} leave the library. Playlists already in Spotify " +
                "or Tidal stay there.")
        },
        confirmButton = { TextButton(onClick = remove) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}
