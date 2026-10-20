package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Screen(val label: String, val icon: ImageVector) {
    PICKS("Picks", Icons.Filled.AutoAwesome),
    LIBRARY("Library", Icons.Filled.Album),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(viewModel: MainViewModel, open: (String) -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(if (ui.server.complete) Screen.PICKS else Screen.SETTINGS) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.label) },
                actions = { PersonSwitcher(ui, viewModel::choosePerson) },
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = screen == entry,
                        onClick = { screen = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (screen) {
                Screen.PICKS -> PicksScreen(ui, viewModel, open)
                Screen.LIBRARY -> LibraryScreen(ui, viewModel, open)
                Screen.SETTINGS -> SettingsScreen(ui, viewModel, open)
            }
        }
    }

    ui.moreLikeThis?.let { MoreLikeThisSheet(it, viewModel, open) }
}

@Composable
private fun PersonSwitcher(ui: UiState, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (ui.people.isEmpty()) return
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Person, contentDescription = "Whose music")
            Text(ui.person?.name ?: "Choose", Modifier.padding(start = 4.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ui.people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(person.name) },
                    onClick = {
                        expanded = false
                        choose(person.id)
                    },
                )
            }
        }
    }
}
