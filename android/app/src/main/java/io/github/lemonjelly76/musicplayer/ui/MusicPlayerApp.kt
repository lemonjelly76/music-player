package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
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

    LaunchedEffect(ui.pendingLink) {
        ui.pendingLink?.let {
            open(it)
            viewModel.linkOpened()
        }
    }

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
                actions = { Switchers(ui, viewModel, showCategory = screen == Screen.PICKS) },
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
                Screen.SETTINGS -> SettingsScreen(ui, viewModel)
            }
        }
    }

    ui.moreLikeThis?.let { MoreLikeThisSheet(it, viewModel, open) }
}

/** Whose music and, on the Picks screen, for which category: "All" or an activity such as Running. */
@Composable
private fun Switchers(ui: UiState, viewModel: MainViewModel, showCategory: Boolean) {
    if (ui.people.isEmpty()) return
    Row {
        if (showCategory) Switcher(
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            description = "Category",
            current = ui.chosenCategory?.name ?: "All",
            options = listOf(GENERAL to "All") + ui.categories.map { it.id to it.name },
            choose = viewModel::chooseCategory,
        )
        Switcher(
            icon = Icons.Filled.Person,
            description = "Whose music",
            current = ui.person?.name ?: "Choose",
            options = ui.people.map { it.id to it.name },
            choose = viewModel::choosePerson,
        )
    }
}

@Composable
private fun Switcher(
    icon: ImageVector,
    description: String,
    current: String,
    options: List<Pair<String, String>>,
    choose: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(icon, contentDescription = description)
            Text(current, Modifier.padding(start = 4.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        choose(id)
                    },
                )
            }
        }
    }
}
