package io.github.lemonjelly76.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.ApiClient
import io.github.lemonjelly76.musicplayer.data.Candidate
import io.github.lemonjelly76.musicplayer.data.Category
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Person
import io.github.lemonjelly76.musicplayer.data.PickList
import io.github.lemonjelly76.musicplayer.data.ServerSettings
import io.github.lemonjelly76.musicplayer.data.SettingsStore
import io.github.lemonjelly76.musicplayer.data.Tracking
import io.github.lemonjelly76.musicplayer.data.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "All" picks are the person's general list, which the server files under no category. */
const val GENERAL = "none"

data class MoreLikeThis(val seed: Album, val loading: Boolean, val candidates: List<Candidate>)

data class UiState(
    val server: ServerSettings = ServerSettings("", "", ""),
    val people: List<Person> = emptyList(),
    val personId: String? = null,
    val categories: List<Category> = emptyList(),
    val tab: String = GENERAL,
    val list: PickList? = null,
    val library: List<LibraryAlbum> = emptyList(),
    val tracking: Tracking? = null,
    val moreLikeThis: MoreLikeThis? = null,
    val loading: Boolean = false,
    val message: String? = null,
) {
    val person: Person? get() = people.firstOrNull { it.id == personId }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val state = MutableStateFlow(UiState(server = store.server(), personId = store.personId()))
    val ui: StateFlow<UiState> = state.asStateFlow()

    private val api get() = ApiClient(state.value.server)

    fun saveServer(settings: ServerSettings) {
        store.saveServer(settings)
        state.update { it.copy(server = settings) }
        refresh()
    }

    fun refresh() = load {
        val people = api.people()
        val categories = api.categories()
        val chosen = people.firstOrNull { it.id == state.value.personId } ?: people.firstOrNull()
        state.update { it.copy(people = people, categories = categories, personId = chosen?.id) }
        chosen?.let { loadPerson(it.id) }
    }

    fun choosePerson(id: String) = load {
        store.savePersonId(id)
        state.update { it.copy(personId = id, list = null, library = emptyList(), tracking = null) }
        loadPerson(id)
    }

    fun chooseTab(tab: String) = load {
        state.update { it.copy(tab = tab, list = null) }
        loadList()
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
            state.update { it.copy(moreLikeThis = MoreLikeThis(album, false, candidates)) }
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

    /** The sign-in page for the person's streaming service, for the activity to open in the browser. */
    fun connect(open: (String) -> Unit) = load {
        val personId = state.value.personId ?: return@load
        open(api.connectUrl(personId))
    }

    fun clearMessage() = state.update { it.copy(message = null) }

    private suspend fun loadPerson(id: String) {
        state.update { it.copy(tracking = api.tracking(id)) }
        reloadLibrary(id)
        loadList()
    }

    private suspend fun loadList() {
        val personId = state.value.personId ?: return
        val list = api.lists(personId, state.value.tab).firstOrNull()
        state.update { it.copy(list = list) }
    }

    private suspend fun reloadLibrary(personId: String) {
        val library = api.library(personId)
        state.update { it.copy(library = library) }
    }

    /** Runs a server call, showing progress and turning failures into a message. */
    private fun load(block: suspend () -> Unit) {
        viewModelScope.launch {
            state.update { it.copy(loading = true) }
            try {
                block()
            } catch (e: Exception) {
                state.update { it.copy(message = e.message ?: "Something went wrong") }
            } finally {
                state.update { it.copy(loading = false) }
            }
        }
    }
}
