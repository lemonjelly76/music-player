package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The quick reasons offered after Not for me, kept with the rating for Claude to read. */
fun dislikeReasons(activity: String): List<String> =
    listOf("Too slow", "Too intense", "Wrong for $activity", "Not keen on the vocals", "Not my style")

/** Asks, in one tap, why what was just turned down is not for them; skipping keeps the rating as it is. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WhyNotSheet(asked: AskWhy, activity: String, viewModel: MainViewModel) {
    ModalBottomSheet(onDismissRequest = viewModel::skipWhy, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Text("Why isn't ${asked.title} for $activity?", Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Claude reads this when picking for $activity.", Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dislikeReasons(activity).forEach { reason ->
                Pill(reason, null, selected = false) { viewModel.explainDislike(reason) }
            }
        }
        TextButton(onClick = viewModel::skipWhy, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Skip") }
        Spacer(Modifier.height(24.dp))
    }
}
