package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lemonjelly76.musicplayer.data.ActivityKind
import io.github.lemonjelly76.musicplayer.data.GENERAL
import io.github.lemonjelly76.musicplayer.data.Person

private enum class Screen(val label: String, val icon: ImageVector, val chosenIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    SEARCH("Search", Icons.Outlined.Search, Icons.Filled.Search),
    LIBRARY("Library", AppIcons.LibraryMusicOutlined, AppIcons.LibraryMusic),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(viewModel: MainViewModel, open: (String) -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var settingsOpen by rememberSaveable { mutableStateOf(!ui.server.complete) }
    // A playlist or earlier album list opened from Home; it closes by itself when the activity or person changes.
    var openListId by rememberSaveable { mutableStateOf<String?>(null) }
    var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val openList = ui.lists.firstOrNull { it.id == openListId }.takeIf { screen == Screen.HOME }
    val openPlaylist = ui.playlists.firstOrNull { it.id == openPlaylistId }.takeIf { screen == Screen.HOME }
    val sorting = ui.sorting
    val listening = ui.listening
    val chooseScreen = { chosen: Screen ->
        screen = chosen
        openListId = null
        openPlaylistId = null
        viewModel.stopSorting()
        viewModel.closeListening()
    }
    val snackbar = remember { SnackbarHostState() }
    // An activity held down, waiting for the person to confirm removing it.
    var removing by remember { mutableStateOf<Activity?>(null) }
    val chips = @Composable {
        ActivityChips(activities(ui), ui.category, viewModel::chooseCategory, remove = { removing = it })
    }

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

    // Back from signing in to a streaming service, or from listening: show the latest.
    LifecycleResumeEffect(Unit) {
        if (viewModel.ui.value.server.complete) viewModel.refresh()
        onPauseOrDispose { }
    }

    SystemBack(enabled = settingsOpen || sorting != null || listening != null || openList != null ||
        openPlaylist != null) {
        when {
            settingsOpen -> settingsOpen = false
            sorting != null -> viewModel.stopSorting()
            listening != null -> viewModel.closeListening()
            openPlaylist != null -> openPlaylistId = null
            else -> openListId = null
        }
    }

    val barColors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    // Phones get tabs along the bottom; wider windows, such as the desktop app, a rail down the side.
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
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
                } else if (sorting != null) {
                    TopAppBar(
                        title = { Text("Sort for ${ui.activityName}", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = viewModel::stopSorting) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = barColors,
                    )
                } else if (listening != null) {
                    TopAppBar(
                        title = { Text("Your listening", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = viewModel::closeListening) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = barColors,
                    )
                } else if (openPlaylist != null) {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = { openPlaylistId = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = barColors,
                    )
                } else if (openList != null) {
                    TopAppBar(
                        title = { Text(shortDate(openList.createdAt), fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { openListId = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = barColors,
                    )
                } else {
                    TopAppBar(
                        title = { Text(screen.label, fontWeight = FontWeight.Bold) },
                        actions = {
                            PersonSwitcher(ui.people, ui.person, viewModel::choosePerson, viewModel::openListening)
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                            }
                        },
                        colors = barColors,
                    )
                }
            },
            bottomBar = {
                if (!settingsOpen && !wide) BottomTabs(screen, chooseScreen)
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (!settingsOpen && wide) SideTabs(screen, chooseScreen)
                Column(Modifier.fillMaxSize()) {
                    if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    when {
                        settingsOpen -> SettingsScreen(ui, viewModel)
                        sorting != null -> SortScreen(sorting, ui.activityName, viewModel, open)
                        listening != null -> Column {
                            chips()
                            ListeningScreen(listening, ui, viewModel, open)
                        }
                        openPlaylist != null -> PlaylistScreen(openPlaylist, ui.person?.name, open)
                        openList != null -> PickListScreen(openList, ui.person?.name, viewModel, open)
                        else -> Refreshable(ui.loading, viewModel::refresh) {
                            Column {
                                if (ui.people.isNotEmpty() && ui.categories.isNotEmpty()) chips()
                                when (screen) {
                                    Screen.HOME -> HomeScreen(ui, viewModel, open,
                                        showList = { openListId = it }, showPlaylist = { openPlaylistId = it },
                                        seeLibrary = { screen = Screen.LIBRARY })
                                    Screen.SEARCH -> SearchScreen(ui, viewModel, open)
                                    Screen.LIBRARY -> LibraryScreen(ui, viewModel, open)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ui.moreLikeThis?.let { MoreLikeThisSheet(it, viewModel, open) }
    removing?.let { activity ->
        RemoveActivityDialog(activity, remove = { viewModel.removeCategory(activity.id) }, close = { removing = null })
    }
}

/** Removing an activity cannot be undone and affects everyone, so it is confirmed first. */
@Composable
private fun RemoveActivityDialog(activity: Activity, remove: () -> Unit, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Remove ${activity.name}?") },
        text = {
            Text("This removes it for everyone in the household, with the ratings, albums to try and playlists " +
                "for it. Albums rated only for ${activity.name} leave the library. Playlists already in Spotify " +
                "or Tidal stay there.")
        },
        confirmButton = {
            TextButton(onClick = { close(); remove() }) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

/** Pull down to fetch what Claude has written since; the spinner shows only for a pull, not for every rating. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refreshable(loading: Boolean, refresh: () -> Unit, content: @Composable () -> Unit) {
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (!loading) pulled = false }
    PullToRefreshBox(isRefreshing = pulled, onRefresh = { pulled = true; refresh() }) { content() }
}

@Composable
private fun BottomTabs(screen: Screen, choose: (Screen) -> Unit) {
    NavigationBar(containerColor = Color.Black.copy(alpha = 0.9f)) {
        Screen.entries.forEach { entry ->
            val chosen = screen == entry
            NavigationBarItem(
                selected = chosen,
                onClick = { choose(entry) },
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
}

@Composable
private fun SideTabs(screen: Screen, choose: (Screen) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
        Screen.entries.forEach { entry ->
            val chosen = screen == entry
            NavigationRailItem(
                selected = chosen,
                onClick = { choose(entry) },
                icon = { Icon(if (chosen) entry.chosenIcon else entry.icon, contentDescription = null) },
                label = { Text(entry.label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

/** General, then each of the person's categories, such as Running. */
private fun activities(ui: UiState): List<Activity> =
    listOf(Activity(GENERAL, "General", activityIcon(ActivityKind.GENERAL))) +
        ui.categories.map { Activity(it.id, it.name, activityIcon(ActivityKind.of(it.name))) }

private fun activityIcon(kind: ActivityKind): ImageVector = when (kind) {
    ActivityKind.GENERAL -> AppIcons.LibraryMusic
    ActivityKind.RUNNING -> AppIcons.DirectionsRun
    ActivityKind.WORKOUT -> AppIcons.FitnessCenter
    ActivityKind.CHILL -> AppIcons.SelfImprovement
    ActivityKind.DINNER -> AppIcons.DinnerDining
    ActivityKind.DRIVING -> AppIcons.DirectionsCar
    ActivityKind.FOCUS -> AppIcons.Psychology
    ActivityKind.PARTY -> AppIcons.Celebration
    ActivityKind.SLEEP -> AppIcons.Bedtime
    ActivityKind.OTHER -> AppIcons.MusicNote
}

/**
 * Whose music: the current person's initial in a circle, opening a list of everyone in the household and, as in
 * streaming apps' profile menus, what the current person has been listening to.
 */
@Composable
private fun PersonSwitcher(people: List<Person>, current: Person?, choose: (String) -> Unit, showListening: () -> Unit) {
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
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Your listening") },
                leadingIcon = { Icon(AppIcons.BarChart, contentDescription = null) },
                onClick = {
                    expanded = false
                    showListening()
                },
            )
        }
    }
}
