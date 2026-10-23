package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Something to do with an album, a playlist or a list, from its ⋯ menu or by holding it. One that deletes something
 * has a [confirm] question, asked first, and shows in red.
 */
data class MenuAction(val label: String, val confirm: Confirm? = null, val run: () -> Unit)

/** What deleting will do, asked before it is done; [button] goes ahead. */
data class Confirm(val title: String, val text: String, val button: String)

/** Tapping does [onClick]; holding, with a buzz, does [onHold], as streaming apps open an item's actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.holdable(holdLabel: String, onHold: (() -> Unit)?, onClick: (() -> Unit)?): Modifier {
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        enabled = onClick != null || onHold != null,
        onClick = { onClick?.invoke() },
        onLongClickLabel = holdLabel.takeIf { onHold != null },
        onLongClick = onHold?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
    )
}

/**
 * An item's actions as a sheet while it is [held]: its cover, title and [subtitle], then each action. Choosing one
 * that deletes asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeldActions(
    held: Boolean,
    release: () -> Unit,
    title: String,
    subtitle: String,
    actions: List<MenuAction>,
    art: @Composable () -> Unit,
) {
    var confirming by remember { mutableStateOf<MenuAction?>(null) }
    if (held) {
        ModalBottomSheet(onDismissRequest = release, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(56.dp)) { art() }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // A long menu, as a library album's with its ratings, scrolls rather than running under the gesture bar.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                actions.forEach { action ->
                    val colour = if (action.confirm != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    Row(Modifier.fillMaxWidth().clickable {
                        release()
                        if (action.confirm != null) confirming = action else action.run()
                    }.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (action.confirm != null) Icon(Icons.Outlined.Delete, contentDescription = null, tint = colour)
                        Text(action.label, style = MaterialTheme.typography.bodyLarge, color = colour)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    confirming?.let { action -> ConfirmDialog(action) { confirming = null } }
}

/** The ⋯ button and its menu; an action that deletes asks first. */
@Composable
fun ActionsMenu(actions: List<MenuAction>) {
    var expanded by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<MenuAction?>(null) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(action.label, color = if (action.confirm != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface)
                    },
                    onClick = {
                        expanded = false
                        if (action.confirm != null) confirming = action else action.run()
                    },
                )
            }
        }
    }
    confirming?.let { action -> ConfirmDialog(action) { confirming = null } }
}

@Composable
private fun ConfirmDialog(action: MenuAction, close: () -> Unit) {
    val confirm = action.confirm ?: return
    AlertDialog(
        onDismissRequest = close,
        title = { Text(confirm.title) },
        text = { Text(confirm.text) },
        confirmButton = {
            TextButton(onClick = { close(); action.run() }) {
                Text(confirm.button, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}
