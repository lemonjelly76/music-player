package io.github.lemonjelly76.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lemonjelly76.musicplayer.data.ActivityKind
import io.github.lemonjelly76.musicplayer.data.GENERAL
import io.github.lemonjelly76.musicplayer.data.Person

private enum class Screen(val label: String, val icon: ImageVector, val chosenIcon: ImageVector) {
    PICKS("Picks", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    LIBRARY("Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(viewModel: MainViewModel, open: (String) -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.PICKS) }
    var settingsOpen by rememberSaveable { mutableStateOf(!ui.server.complete) }
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

    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    val barColors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    Scaffold(
        topBar = {
            if (settingsOpen) {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { settingsOpen = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = barColors,
                )
            } else {
                TopAppBar(
                    title = { Text(screen.label, fontWeight = FontWeight.Bold) },
                    actions = {
                        PersonSwitcher(ui.people, ui.person, viewModel::choosePerson)
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = barColors,
                )
            }
        },
        bottomBar = {
            if (!settingsOpen) NavigationBar(containerColor = Color.Black.copy(alpha = 0.9f)) {
                Screen.entries.forEach { entry ->
                    val chosen = screen == entry
                    NavigationBarItem(
                        selected = chosen,
                        onClick = { screen = entry },
                        icon = { Icon(if (chosen) entry.chosenIcon else entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (settingsOpen) {
                SettingsScreen(ui, viewModel)
            } else {
                if (ui.people.isNotEmpty() && ui.categories.isNotEmpty()) {
                    ActivityChips(activities(ui), ui.category, viewModel::chooseCategory)
                }
                when (screen) {
                    Screen.PICKS -> PicksScreen(ui, viewModel, open)
                    Screen.LIBRARY -> LibraryScreen(ui, viewModel, open)
                }
            }
        }
    }

    ui.moreLikeThis?.let { MoreLikeThisSheet(it, viewModel, open) }
}

/** General, then each of the person's categories, such as Running. */
private fun activities(ui: UiState): List<Activity> =
    listOf(Activity(GENERAL, "General", activityIcon(ActivityKind.GENERAL))) +
        ui.categories.map { Activity(it.id, it.name, activityIcon(ActivityKind.of(it.name))) }

private fun activityIcon(kind: ActivityKind): ImageVector = when (kind) {
    ActivityKind.GENERAL -> Icons.Filled.LibraryMusic
    ActivityKind.RUNNING -> Icons.AutoMirrored.Filled.DirectionsRun
    ActivityKind.WORKOUT -> Icons.Filled.FitnessCenter
    ActivityKind.CHILL -> Icons.Filled.SelfImprovement
    ActivityKind.DINNER -> Icons.Filled.DinnerDining
    ActivityKind.DRIVING -> Icons.Filled.DirectionsCar
    ActivityKind.FOCUS -> Icons.Filled.Psychology
    ActivityKind.PARTY -> Icons.Filled.Celebration
    ActivityKind.SLEEP -> Icons.Filled.Bedtime
    ActivityKind.OTHER -> Icons.Filled.MusicNote
}

/** Whose music: the current person's initial in a circle, opening a list of everyone in the household. */
@Composable
private fun PersonSwitcher(people: List<Person>, current: Person?, choose: (String) -> Unit) {
    if (people.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Initial(current?.name ?: "?", 32.dp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(person.name) },
                    leadingIcon = { Initial(person.name, 28.dp) },
                    trailingIcon = if (person.id == current?.id) ({ Icon(Icons.Filled.Check, null) }) else null,
                    onClick = {
                        expanded = false
                        choose(person.id)
                    },
                )
            }
        }
    }
}
