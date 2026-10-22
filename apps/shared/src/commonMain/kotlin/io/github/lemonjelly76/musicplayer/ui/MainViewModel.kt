package io.github.lemonjelly76.musicplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.ApiClient
import io.github.lemonjelly76.musicplayer.data.ApiException
import io.github.lemonjelly76.musicplayer.data.Candidate
import io.github.lemonjelly76.musicplayer.data.Category
import io.github.lemonjelly76.musicplayer.data.Charts
import io.github.lemonjelly76.musicplayer.data.Found
import io.github.lemonjelly76.musicplayer.data.GENERAL
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Listening
import io.github.lemonjelly76.musicplayer.data.Person
import io.github.lemonjelly76.musicplayer.data.PickList
import io.github.lemonjelly76.musicplayer.data.Playlist
import io.github.lemonjelly76.musicplayer.data.PlaylistCopy
import io.github.lemonjelly76.musicplayer.data.ServerSettings
import io.github.lemonjelly76.musicplayer.data.Service
import io.github.lemonjelly76.musicplayer.data.SettingsStore
import io.github.lemonjelly76.musicplayer.data.Track
import io.github.lemonjelly76.musicplayer.data.Tracking
import io.github.lemonjelly76.musicplayer.data.Rating
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The list with the activity [id] moved [by] places, stopping at either end. */
fun List<Category>.moved(id: String, by: Int): List<Category> {
    val from = indexOfFirst { it.id == id }
    if (from < 0) return this
    val to = (from + by).coerceIn(0, lastIndex)
    return toMutableList().apply { add(to, removeAt(from)) }
}

data class MoreLikeThis(val seed: Album, val loading: Boolean, val candidates: List<Candidate>)

/** Albums or songs, as Search and Library each show one or the other. */
enum class MusicKind(val label: String) { ALBUMS("Albums"), SONGS("Songs") }

/** A catalogue search; [results] or [songs], by [kind], is null until the first answer comes. */
data class Search(
    val query: String = "",
    val kind: MusicKind = MusicKind.ALBUMS,
    val results: List<Found>? = null,
    val songs: List<Track>? = null,
) {
    val answered: Boolean get() = if (kind == MusicKind.ALBUMS) results != null else songs != null
}

data class UiState(
    val server: ServerSettings = ServerSettings("", "", ""),
    val people: List<Person> = emptyList(),
    val personId: String? = null,
    val categories: List<Category> = emptyList(),
    val services: List<Service> = emptyList(),
    /** The chosen category's id, or [GENERAL]. */
    val category: String = GENERAL,
    /** Whether the server answered with the saved login, so its settings can fold away. */
    val serverConnected: Boolean = false,
    /** The activity's lists of albums to try, newest first. */
    val lists: List<PickList> = emptyList(),
    /** The activity's song playlists, newest first; those with a slot are in the person's streaming app. */
    val playlists: List<Playlist> = emptyList(),
    val library: List<LibraryAlbum> = emptyList(),
    /** What the person turned down for the activity, so a dislike can be taken back. */
    val disliked: List<LibraryAlbum> = emptyList(),
    /** Albums kept elsewhere that have no rating for the activity yet. */
    val toSort: List<LibraryAlbum> = emptyList(),
    /** Songs with a rating that applies to the activity, loved first. */
    val songs: List<Track> = emptyList(),
    val search: Search = Search(),
    val sorting: SortSession? = null,
    /** The week's chart, for Home on General; null until it comes or if the server has none. */
    val charts: Charts? = null,
    /** What the person played most for the activity lately, for Home; null until it comes or if it fails. */
    val onRepeat: Listening? = null,
    /** The listening page, while it is open. */
    val listening: ListeningPage? = null,
    val tracking: Tracking? = null,
    /** Where each of the person's activities has its newest playlist copied. */
    val copies: List<PlaylistCopy> = emptyList(),
    val moreLikeThis: MoreLikeThis? = null,
    val loading: Boolean = false,
    val message: String? = null,
    /** A sign-in page the screen should open in the browser, then clear. */
    val pendingLink: String? = null,
) {
    val person: Person? get() = people.firstOrNull { it.id == personId }
    val chosenCategory: Category? get() = categories.firstOrNull { it.id == category }
    val activityName: String get() = chosenCategory?.name ?: "General"
    val latest: PickList? get() = lists.firstOrNull()
    /** The chosen activity's copy, when its playlists are copied to another service. */
    val copy: PlaylistCopy? get() = copies.firstOrNull { (it.categoryId ?: GENERAL) == category }
}

/** What both apps show and do; [http] is closed with the view model. */
class MainViewModel(private val store: SettingsStore, private val http: HttpClient = HttpClient()) : ViewModel() {

    private val state = MutableStateFlow(UiState(server = store.server(), personId = store.personId()))
    val ui: StateFlow<UiState> = state.asStateFlow()

    private val api = ApiClient({ state.value.server }, http)
    private var running = 0

    override fun onCleared() = http.close()

    fun saveServer(entered: ServerSettings) {
        val settings = entered.normalised()
        store.saveServer(settings)
        state.update { it.copy(server = settings, serverConnected = false) }
        refresh()
    }

    fun refresh() = load {
        val (people, categories, services) = coroutineScope {
            val people = async { api.people() }
            val categories = async { api.categories() }
            // Only Settings needs these, so a server without the endpoint still shows picks.
            val services = async { runCatching { api.services() }.getOrDefault(emptyList()) }
            Triple(people.await(), categories.await(), services.await())
        }
        val chosen = people.firstOrNull { it.id == state.value.personId } ?: people.firstOrNull()
        val category = state.value.category.takeIf { id -> id == GENERAL || categories.any { it.id == id } } ?: GENERAL
        state.update {
            it.copy(people = people, categories = categories, services = services, personId = chosen?.id,
                category = category, serverConnected = true)
        }
        chosen?.let { loadPerson(it.id) }
    }

    fun choosePerson(id: String) = load {
        store.savePersonId(id)
        state.update {
            it.copy(personId = id, tracking = null, copies = emptyList(), sorting = null, charts = null)
                .withoutActivityAlbums()
                .withoutListening()
        }
        coroutineScope {
            launch { loadPerson(id) }
            launch { searchAgain() }
        }
    }

    /** The activity every tab shows and rates for. */
    fun chooseCategory(category: String) = load {
        state.update {
            it.copy(category = category, sorting = null).withoutActivityAlbums().withoutListening()
        }
        coroutineScope {
            launch { loadLists() }
            launch { state.value.personId?.let { reloadLibrary(it) } }
            launch { searchAgain() }
            launch { loadListening() }
        }
    }

    /**
     * Moves an activity [by] places, earlier when negative, for the whole household; the chips move at once and the
     * server's order is kept once it answers.
     */
    fun moveCategory(id: String, by: Int) {
        val moved = state.value.categories.moved(id, by)
        if (moved == state.value.categories) return
        state.update { it.copy(categories = moved) }
        load {
            val saved = api.reorderCategories(moved.map { it.id })
            state.update { it.copy(categories = saved) }
        }
    }

    /** Renames the activity for the whole household; its playlists take the new name too. */
    fun renameCategory(id: String, name: String) = load {
        val renamed = api.renameCategory(id, name)
        state.update { current ->
            current.copy(
                categories = current.categories.map { if (it.id == id) renamed else it }.sortedBy { it.name.lowercase() },
                message = "Renamed to ${renamed.name}",
            )
        }
        if (state.value.category == id) loadLists()
    }

    /** Removes the activity for the whole household; showing it, the app goes back to General. */
    fun removeCategory(id: String) {
        val removed = state.value.categories.firstOrNull { it.id == id } ?: return
        load {
            api.removeCategory(id)
            state.update {
                it.copy(categories = it.categories - removed, message = "${removed.name} removed")
            }
            if (state.value.category == id) chooseCategory(GENERAL)
        }
    }

    private fun UiState.withoutActivityAlbums() = copy(lists = emptyList(), playlists = emptyList(),
        library = emptyList(), disliked = emptyList(), toSort = emptyList(), songs = emptyList(),
        search = search.copy(results = null, songs = null))

    private fun UiState.withoutListening() = copy(onRepeat = null, listening = listening?.copy(stats = null))

    /**
     * Sets the person's streaming service; they then connect their own account to it. The choice shows at once, and
     * a reply to an earlier, overtaken tap is ignored.
     */
    fun chooseService(service: String) {
        val personId = state.value.personId ?: return
        replacePerson(personId) { it.copy(service = service, connected = false) }
        load {
            val changed = api.setService(personId, service)
            if (state.value.people.firstOrNull { it.id == personId }?.service == service) {
                replacePerson(personId) { changed }
            }
        }
    }

    private fun replacePerson(id: String, change: (Person) -> Person) = state.update { current ->
        current.copy(people = current.people.map { if (it.id == id) change(it) else it })
    }

    /**
     * Rates any album for the chosen activity; null takes the rating away. Picks show it at once, and the library is
     * reloaded because a rating can add or remove an album there.
     */
    fun rate(album: Album, rating: Rating?) = load { saveRating(album, rating) }

    /** Loves a suggestion for the chosen activity, which puts it in the library there. */
    fun love(album: Album) = load {
        saveRating(album, Rating.LOVE)
        state.update { it.copy(message = "${album.title} added to ${it.activityName}") }
    }

    private suspend fun saveRating(album: Album, rating: Rating?) {
        val personId = state.value.personId ?: return
        api.rate(personId, album.ref, state.value.category, rating)
        state.update { current ->
            current.copy(
                lists = current.lists.map { list ->
                    list.copy(picks = list.picks.map { if (it.album.ref == album.ref) it.copy(rating = rating) else it })
                },
                search = current.search.copy(results = current.search.results?.map {
                    if (it.album.ref == album.ref) it.copy(rating = rating, general = false) else it
                }),
                charts = current.charts?.takeIf { current.category == GENERAL }?.let { charts ->
                    charts.copy(albums = charts.albums.map {
                        if (it.album.album.ref == album.ref) it.copy(album = it.album.copy(rating = rating)) else it
                    })
                } ?: current.charts,
            )
        }
        reloadLibrary(personId)
    }

    /** Looks [query] up in the person's streaming catalogue; a blank one clears the results. */
    fun search(query: String) {
        val text = query.trim()
        state.update { it.copy(search = Search(text, it.search.kind)) }
        if (text.isNotEmpty()) load { searchAgain() }
    }

    fun chooseSearchKind(kind: MusicKind) {
        state.update { it.copy(search = Search(it.search.query, kind)) }
        load { searchAgain() }
    }

    /** Runs the current search for the chosen person and activity, keeping only the latest answer. */
    private suspend fun searchAgain() {
        val personId = state.value.personId ?: return
        val query = state.value.search.query.takeIf { it.isNotEmpty() } ?: return
        val kind = state.value.search.kind
        val category = state.value.category
        val answer = when (kind) {
            MusicKind.ALBUMS -> Search(query, kind, results = api.search(personId, query, category))
            MusicKind.SONGS -> Search(query, kind, songs = api.searchSongs(personId, query, category))
        }
        state.update {
            if (it.personId == personId && it.category == category && it.search.query == query && it.search.kind == kind) {
                it.copy(search = answer)
            } else it
        }
    }

    /**
     * Rates a song for the chosen activity; null takes the rating away. Playlists and search show it at once, and
     * the activity's songs are reloaded, as is everything when a rating is taken away and General may stand in.
     */
    fun rateSong(track: Track, rating: Rating?) = load {
        val personId = state.value.personId ?: return@load
        api.rateSong(personId, track, state.value.category, rating)
        val rated = { song: Track -> if (song.ref == track.ref) song.copy(rating = rating, general = false) else song }
        state.update { current ->
            current.copy(
                playlists = current.playlists.map { playlist -> playlist.copy(tracks = playlist.tracks.map(rated)) },
                search = current.search.copy(songs = current.search.songs?.map(rated)),
                charts = current.charts?.takeIf { current.category == GENERAL }
                    ?.let { charts -> charts.copy(songs = charts.songs.map { it.copy(song = rated(it.song)) }) }
                    ?: current.charts,
            )
        }
        coroutineScope {
            launch { reloadLibrary(personId) }
            if (rating == null) {
                launch { loadLists() }
                launch { searchAgain() }
            }
        }
    }

    /** Goes through the albums to sort for the chosen activity, one at a time. */
    fun startSorting() = state.update { it.copy(sorting = SortSession(it.category, it.toSort)) }

    /** Rates the album being sorted and moves on; null skips it. */
    fun sort(rating: Rating?) {
        val session = state.value.sorting ?: return
        val album = session.current?.album ?: return
        state.update { it.copy(sorting = session.next(rated = rating != null)) }
        if (rating != null) load { saveRating(album, rating) }
    }

    fun stopSorting() = state.update { it.copy(sorting = null) }

    /** Opens the listening page for the chosen activity, over the last four weeks to begin with. */
    fun openListening() {
        state.update { it.copy(listening = ListeningPage()) }
        load { loadListeningPage() }
    }

    fun choosePeriod(period: Period) {
        state.update { it.copy(listening = ListeningPage(period)) }
        load { loadListeningPage() }
    }

    fun closeListening() = state.update { it.copy(listening = null) }

    fun moreLikeThis(album: Album) {
        val personId = state.value.personId ?: return
        state.update { it.copy(moreLikeThis = MoreLikeThis(album, true, emptyList())) }
        load {
            val candidates = api.moreLikeThis(personId, album.artist, album.title, state.value.category)
            // Closed or replaced while loading: leave it closed.
            state.update {
                if (it.moreLikeThis?.seed == album) it.copy(moreLikeThis = MoreLikeThis(album, false, candidates)) else it
            }
        }
    }

    fun closeMoreLikeThis() = state.update { it.copy(moreLikeThis = null) }

    /** One switch for all of the person's plays, whatever the activity. */
    fun setPlayCounting(enabled: Boolean) = load {
        val personId = state.value.personId ?: return@load
        val tracking = api.setPlayCounting(personId, enabled)
        state.update { it.copy(tracking = tracking) }
    }

    /** Asks for the sign-in page for the person's streaming service, which the screen then opens. */
    /** Opens the sign-in page for the person's own service, or for [service] to connect a second one. */
    fun connect(service: String? = null) = load {
        val personId = state.value.personId ?: return@load
        val link = api.connectUrl(personId, service)
        state.update { it.copy(pendingLink = link) }
    }

    /** Copies the activity's newest playlist to [service] from now on, or stops when it is null. */
    fun copyPlaylists(service: String?) = load {
        val personId = state.value.personId ?: return@load
        val status = api.copyPlaylists(personId, state.value.category.takeUnless { it == GENERAL }, service)
        loadCopies(personId)
        state.update { it.copy(message = status.replaceFirstChar { first -> first.uppercase() }) }
    }

    /** Only the playlist page needs these, so a server without copies still shows everything else. */
    private suspend fun loadCopies(personId: String) {
        val copies = runCatching { api.playlistCopies(personId) }.getOrDefault(emptyList())
        state.update { if (it.personId == personId) it.copy(copies = copies) else it }
    }

    fun linkOpened() = state.update { it.copy(pendingLink = null) }

    fun clearMessage() = state.update { it.copy(message = null) }

    private suspend fun loadPerson(id: String) {
        val tracking = api.tracking(id)
        state.update { if (it.personId == id) it.copy(tracking = tracking) else it }
        loadCopies(id)
        reloadLibrary(id)
        loadLists()
        loadListening()
        loadCharts()
    }

    /** Only Home's chart needs this, so a server without charts still shows everything else. */
    private suspend fun loadCharts() {
        val personId = state.value.personId ?: return
        val charts = try {
            api.charts(personId)
        } catch (e: ApiException) {
            return
        }
        state.update { if (it.personId == personId) it.copy(charts = charts) else it }
    }

    /**
     * Puts the playlist in the person's streaming app as its activity's playlist [slot]; the activity's playlists
     * are reloaded, as the one there before has swapped places with it or left.
     */
    fun putInSlot(playlist: Playlist, slot: Int) = load {
        val personId = state.value.personId ?: return@load
        api.putPlaylistInSlot(personId, playlist.id, slot)
        loadLists()
    }

    /** Turns the person's weekly chart playlist on or off. */
    fun setChartPlaylist(enabled: Boolean) = load {
        val personId = state.value.personId ?: return@load
        val now = api.setChartPlaylist(personId, enabled)
        state.update { it.copy(charts = it.charts?.copy(playlistWanted = now)) }
    }

    private suspend fun loadListening() = coroutineScope {
        launch { loadOnRepeat() }
        launch { loadListeningPage() }
    }

    /** Only Home's shelf needs this, so a server without play stats still shows everything else. */
    private suspend fun loadOnRepeat() {
        val personId = state.value.personId ?: return
        val category = state.value.category
        val listening = try {
            api.listening(personId, category, Period.MONTH.days)
        } catch (e: ApiException) {
            return
        }
        state.update { if (it.personId == personId && it.category == category) it.copy(onRepeat = listening) else it }
    }

    private suspend fun loadListeningPage() {
        val personId = state.value.personId ?: return
        val period = state.value.listening?.period ?: return
        val category = state.value.category
        val stats = api.listening(personId, category, period.days)
        state.update {
            val page = it.listening
            if (it.personId == personId && it.category == category && page?.period == period) {
                it.copy(listening = page.copy(stats = stats))
            } else it
        }
    }

    /**
     * Answers can arrive after the person or category has changed again; only the latest choice's answer is kept.
     */
    private suspend fun loadLists() = coroutineScope {
        val personId = state.value.personId ?: return@coroutineScope
        val category = state.value.category
        val lists = async { api.lists(personId, category, limit = LISTS_SHOWN) }
        val playlists = async { api.playlists(personId, category, limit = PLAYLISTS_SHOWN) }
        val answers = lists.await() to playlists.await()
        state.update {
            if (it.personId == personId && it.category == category) {
                it.copy(lists = answers.first, playlists = answers.second)
            } else it
        }
    }

    /**
     * The activity's library: albums kept and turned down there, albums still to sort, and rated songs, which any
     * rating can change.
     */
    private suspend fun reloadLibrary(personId: String) = coroutineScope {
        val category = state.value.category
        val library = async { api.library(personId, category) }
        val disliked = async { api.disliked(personId, category) }
        val toSort = async { api.toSort(personId, category) }
        val songs = async { api.songs(personId, category) }
        val answers = Triple(library.await(), disliked.await(), toSort.await())
        val rated = songs.await()
        state.update {
            if (it.personId == personId && it.category == category) {
                it.copy(library = answers.first, disliked = answers.second, toSort = answers.third, songs = rated)
            } else it
        }
    }

    /**
     * Runs a server call, showing progress while any call is running and turning failures into a message. Calls
     * all run on the main thread, so the count needs no locking.
     */
    private fun load(block: suspend () -> Unit) {
        viewModelScope.launch {
            running++
            state.update { it.copy(loading = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                state.update { it.copy(message = e.message, serverConnected = it.serverConnected && !e.unauthorized) }
            } catch (e: Exception) {
                state.update { it.copy(message = e.message ?: "Something went wrong") }
            } finally {
                running--
                state.update { it.copy(loading = running > 0) }
            }
        }
    }

    private companion object {
        const val LISTS_SHOWN = 10

        /** Every playlist the server gives, so any can be put back in the streaming app. */
        const val PLAYLISTS_SHOWN = 20
    }
}
