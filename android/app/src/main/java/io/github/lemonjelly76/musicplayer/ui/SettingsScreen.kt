package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.musicplayer.data.CategoryTracking
import io.github.lemonjelly76.musicplayer.data.ServerSettings

@Composable
fun SettingsScreen(ui: UiState, viewModel: MainViewModel, open: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ServerSection(ui.server, viewModel::saveServer)
        ui.person?.let { person ->
            HorizontalDivider()
            Text(person.name, style = MaterialTheme.typography.titleLarge)
            val service = person.service
            if (service == null) {
                Text("No streaming service yet. Ask Claude to set one with set_person_service.")
            } else {
                Text(if (person.connected) "Connected to ${service.replaceFirstChar { it.uppercase() }}"
                else "${service.replaceFirstChar { it.uppercase() }} is not connected yet, so picks cannot become playlists.")
                Button(onClick = { viewModel.connect(open) }) {
                    Text(if (person.connected) "Connect again" else "Connect ${service.replaceFirstChar { it.uppercase() }}")
                }
            }
            ui.tracking?.let { tracking ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Count my plays", style = MaterialTheme.typography.titleMedium)
                        Text("What you replay helps pick the next albums. Off means nothing is collected.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = tracking.enabled, onCheckedChange = { viewModel.setTracking(null, it) })
                }
                tracking.categories.forEach { CategoryTrackingRow(it, viewModel) }
            }
        }
    }
}

@Composable
private fun ServerSection(saved: ServerSettings, save: (ServerSettings) -> Unit) {
    var url by rememberSaveable { mutableStateOf(saved.serverUrl) }
    var username by rememberSaveable { mutableStateOf(saved.username) }
    var password by rememberSaveable { mutableStateOf(saved.password) }
    Text("Server", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Address, e.g. https://pc.tailnet.ts.net:8444") },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    Button(onClick = { save(ServerSettings(url, username, password)) }) { Text("Save and connect") }
}

/** A category follows the person's setting unless set on or off for itself. */
@Composable
private fun CategoryTrackingRow(category: CategoryTracking, viewModel: MainViewModel) {
    val options = listOf("Default" to null, "On" to true, "Off" to false)
    val current = if (!category.overridden) null else category.enabled
    Column {
        Text(category.name, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (label, value) ->
                SegmentedButton(
                    selected = current == value,
                    onClick = { viewModel.setTracking(category.categoryId, value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(label) },
                )
            }
        }
    }
}
