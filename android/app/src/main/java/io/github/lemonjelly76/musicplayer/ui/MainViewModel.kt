package io.github.lemonjelly76.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.ApiClient
import io.github.lemonjelly76.musicplayer.data.ApiException
import io.github.lemonjelly76.musicplayer.data.Candidate
import io.github.lemonjelly76.musicplayer.data.Category
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Person
import io.github.lemonjelly76.musicplayer.data.PickList
import io.github.lemonjelly76.musicplayer.data.ServerSettings
import io.github.lemonjelly76.musicplayer.data.Service
import io.github.lemonjelly76.musicplayer.data.SettingsStore
import io.github.lemonjelly76.musicplayer.data.Tracking
import io.github.lemonjelly76.musicplayer.data.Verdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "All" is the person's general list, which the server files under no category. */
const val GENERAL = "none"

data class MoreLikeThis(val seed: Album, val loading: Boolean, val candidates: List<Candidate>)

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
    val list: PickList? = null,
    val library: List<LibraryAlbum> = emptyList(),
    val tracking: Tracking? = null,
    val moreLikeThis: MoreLikeThis? = null,
    val loading: Boolean = false,
    val message: String? = null,
    /** A sign-in page the screen should open in the browser, then clear. */
    val pendingLink: String? = null,
) {
    val person: Person? get() = people.firstOrNull { it.id == personId }
    val chosenCategory: Category? get() = categories.firstOrNull { it.id == category }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val state = MutableStateFlow(UiState(server = store.server(), personId = store.personId()))
    val ui: StateFlow<UiState> = state.asStateFlow()

    private val api get() = ApiClient(state.value.server)
    private var running = 0

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
        state.update { it.copy(personId = id, list = null, library = emptyList(), tracking = null) }
        loadPerson(id)
    }

    fun chooseCategory(category: String) = load {
        state.update { it.copy(category = category, list = null) }
        loadList()
    }

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

    fun feedback(pickId: String, verdict: Verdict) = load {
        val judged = api.feedback(pickId, verdict)
        state.update { current ->
            current.copy(list = current.list?.let { list ->
                list.copy(picks = list.picks.map { if (it.id == pickId) judged else it })
            })
        }
        if (verdict == Verdict.LIKED) state.value.personId?.let { reloadLibrary(it) }
    }

    fun moreLikeThis(album: Album) {
        val personId = state.value.personId ?: return
        state.update { it.copy(moreLikeThis = MoreLikeThis(album, true, emptyList())) }
        load {
            val candidates = api.moreLikeThis(personId, album.artist, album.title)
            // Closed or replaced while loading: leave it closed.
            state.update {
                if (it.moreLikeThis?.seed == album) it.copy(moreLikeThis = MoreLikeThis(album, false, candidates)) else it
            }
        }
    }

    fun closeMoreLikeThis() = state.update { it.copy(moreLikeThis = null) }

    fun addToLibrary(album: Album) = load {
        val personId = state.value.personId ?: return@load
        api.addToLibrary(personId, album.ref)
        reloadLibrary(personId)
        state.update { it.copy(message = "${album.title} added to the library") }
    }

    fun setTracking(categoryId: String?, enabled: Boolean?) = load {
        val personId = state.value.personId ?: return@load
        val tracking = api.setTracking(personId, categoryId, enabled)
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
        loadList()
    }

    /**
     * Answers can arrive after the person or category has changed again; only the latest choice's answer is kept.
     */
    private suspend fun loadList() {
        val personId = state.value.personId ?: return
        val category = state.value.category
        val list = api.lists(personId, category).firstOrNull()
        state.update { if (it.personId == personId && it.category == category) it.copy(list = list) else it }
    }

    private suspend fun reloadLibrary(personId: String) {
        val library = api.library(personId)
        state.update { if (it.personId == personId) it.copy(library = library) else it }
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
}
