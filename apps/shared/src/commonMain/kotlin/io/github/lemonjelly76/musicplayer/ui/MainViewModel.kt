package io.github.lemonjelly76.musicplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.ApiClient
import io.github.lemonjelly76.musicplayer.data.ApiException
import io.github.lemonjelly76.musicplayer.data.Candidate
import io.github.lemonjelly76.musicplayer.data.Category
import io.github.lemonjelly76.musicplayer.data.Found
import io.github.lemonjelly76.musicplayer.data.GENERAL
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Person
import io.github.lemonjelly76.musicplayer.data.PickList
import io.github.lemonjelly76.musicplayer.data.ServerSettings
import io.github.lemonjelly76.musicplayer.data.Service
import io.github.lemonjelly76.musicplayer.data.SettingsStore
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

data class MoreLikeThis(val seed: Album, val loading: Boolean, val candidates: List<Candidate>)

/** A catalogue search; [results] is null until the first answer comes. */
data class Search(val query: String = "", val results: List<Found>? = null)

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
    /** The activity's pick lists, newest first; only the newest still has a playlist. */
    val lists: List<PickList> = emptyList(),
    val library: List<LibraryAlbum> = emptyList(),
    /** What the person turned down for the activity, so a dislike can be taken back. */
    val disliked: List<LibraryAlbum> = emptyList(),
    /** Albums kept elsewhere that have no rating for the activity yet. */
    val toSort: List<LibraryAlbum> = emptyList(),
    val search: Search = Search(),
    val sorting: SortSession? = null,
    val tracking: Tracking? = null,
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
            it.copy(personId = id, lists = emptyList(), tracking = null, sorting = null).withoutActivityAlbums()
        }
        coroutineScope {
            launch { loadPerson(id) }
            launch { searchAgain() }
        }
    }

    /** The activity every tab shows and rates for. */
    fun chooseCategory(category: String) = load {
        state.update { it.copy(category = category, lists = emptyList(), sorting = null).withoutActivityAlbums() }
        coroutineScope {
            launch { loadLists() }
            launch { state.value.personId?.let { reloadLibrary(it) } }
            launch { searchAgain() }
        }
    }

    private fun UiState.withoutActivityAlbums() =
        copy(library = emptyList(), disliked = emptyList(), toSort = emptyList(), search = search.copy(results = null))

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
            )
        }
        reloadLibrary(personId)
    }

    /** Looks [query] up in the person's streaming catalogue; a blank one clears the results. */
    fun search(query: String) {
        val text = query.trim()
        state.update { it.copy(search = Search(text)) }
        if (text.isNotEmpty()) load { searchAgain() }
    }

    /** Runs the current search for the chosen person and activity, keeping only the latest answer. */
    private suspend fun searchAgain() {
        val personId = state.value.personId ?: return
        val query = state.value.search.query.takeIf { it.isNotEmpty() } ?: return
        val category = state.value.category
        val results = api.search(personId, query, category)
        state.update {
            if (it.personId == personId && it.category == category && it.search.query == query) {
                it.copy(search = Search(query, results))
            } else it
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
    fun connect() = load {
        val personId = state.value.personId ?: return@load
        val link = api.connectUrl(personId)
        state.update { it.copy(pendingLink = link) }
    }

    fun linkOpened() = state.update { it.copy(pendingLink = null) }

    fun clearMessage() = state.update { it.copy(message = null) }

    private suspend fun loadPerson(id: String) {
        val tracking = api.tracking(id)
        state.update { if (it.personId == id) it.copy(tracking = tracking) else it }
        reloadLibrary(id)
        loadLists()
    }

    /**
     * Answers can arrive after the person or category has changed again; only the latest choice's answer is kept.
     */
    private suspend fun loadLists() {
        val personId = state.value.personId ?: return
        val category = state.value.category
        val lists = api.lists(personId, category, limit = LISTS_SHOWN)
        state.update { if (it.personId == personId && it.category == category) it.copy(lists = lists) else it }
    }

    /** The activity's library, what was turned down there and what is still to sort, which any rating can change. */
    private suspend fun reloadLibrary(personId: String) = coroutineScope {
        val category = state.value.category
        val library = async { api.library(personId, category) }
        val disliked = async { api.disliked(personId, category) }
        val toSort = async { api.toSort(personId, category) }
        val answers = Triple(library.await(), disliked.await(), toSort.await())
        state.update {
            if (it.personId == personId && it.category == category) {
                it.copy(library = answers.first, disliked = answers.second, toSort = answers.third)
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
    }
}
