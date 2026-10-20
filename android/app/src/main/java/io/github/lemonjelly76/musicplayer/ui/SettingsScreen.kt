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
import androidx.compose.material3.TextButton
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
import io.github.lemonjelly76.musicplayer.data.Person
import io.github.lemonjelly76.musicplayer.data.ServerSettings
import io.github.lemonjelly76.musicplayer.data.Service

@Composable
fun SettingsScreen(ui: UiState, viewModel: MainViewModel) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ui.person?.let { person ->
            Text(person.name, style = MaterialTheme.typography.titleLarge)
            StreamingSection(person, ui.services, viewModel)
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
            HorizontalDivider()
        }
        ServerSection(ui.server, ui.serverConnected, viewModel::saveServer)
    }
}

/** Which service the person's picks become playlists in, and connecting their own account to it. */
@Composable
private fun StreamingSection(person: Person, services: List<Service>, viewModel: MainViewModel) {
    Text("Streaming service", style = MaterialTheme.typography.titleMedium)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        services.forEachIndexed { index, service ->
            SegmentedButton(
                selected = person.service == service.name,
                onClick = { if (person.service != service.name) viewModel.chooseService(service.name) },
                shape = SegmentedButtonDefaults.itemShape(index, services.size),
                label = { Text(service.label) },
            )
        }
    }
    val chosen = services.firstOrNull { it.name == person.service }
    when {
        chosen == null -> Text("Choose where your picks become playlists.")
        !chosen.configured -> Text("The server is not set up for ${chosen.label} yet. Add its client id and secret " +
            "to the server's .env, then come back here to connect.")
        person.connected -> {
            Text("Connected to ${chosen.label}. Your picks become playlists there.")
            TextButton(onClick = { viewModel.connect() }) { Text("Connect again") }
        }
        else -> {
            Text("${chosen.label} is not connected yet, so picks cannot become playlists.")
            Button(onClick = { viewModel.connect() }) { Text("Connect ${chosen.label}") }
        }
    }
}

/** Folds away to one line once the server has accepted the login. */
@Composable
private fun ServerSection(saved: ServerSettings, connected: Boolean, save: (ServerSettings) -> Unit) {
    var editing by rememberSaveable(connected) { mutableStateOf(!connected) }
    if (!editing) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Server", style = MaterialTheme.typography.titleMedium)
                Text("Signed in as ${saved.username} at ${saved.serverUrl.removePrefix("https://")}",
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { editing = true }) { Text("Change") }
        }
        return
    }
    var url by rememberSaveable { mutableStateOf(saved.serverUrl) }
    var username by rememberSaveable { mutableStateOf(saved.username) }
    var password by rememberSaveable { mutableStateOf(saved.password) }
    Text("Server", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(),
        label = { Text("Address, e.g. https://pc.tailnet.ts.net:8444") },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") },
        singleLine = true)
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Count plays for ${category.name}", style = MaterialTheme.typography.bodyLarge)
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
