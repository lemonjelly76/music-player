package io.github.lemonjelly76.musicplayer.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/** A failed server call; [unauthorized] when the server refused the saved login. */
class ApiException(message: String, val unauthorized: Boolean = false) : IOException(message)

/**
 * The server's REST API, signed in with the household login. The apps only read what the model wrote and record
 * ratings; they never talk to the model itself. [category] is always a category id, or [MAINSTREAM]. The login is read
 * for each call, so a change in Settings applies at once.
 */
class ApiClient(private val settings: () -> ServerSettings, client: HttpClient) {

    private val http = client.config {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
    }

    suspend fun people(): List<Person> = Json.people(get("api/people"))

    suspend fun services(): List<Service> = Json.services(get("api/services"))

    suspend fun setService(personId: String, service: String): Person =
        Json.person(send(HttpMethod.Put, "api/people/$personId/service", buildJsonObject { put("service", service) }))

    suspend fun categories(): List<Category> = Json.categories(get("api/categories"))

    /** Puts the activities in the household's order, given every activity's id once. */
    suspend fun reorderCategories(ids: List<String>): List<Category> =
        Json.categories(send(HttpMethod.Put, "api/categories/order", buildJsonObject {
            put("ids", JsonArray(ids.map(::JsonPrimitive)))
        }))

    /** Renames an activity for the whole household, and its playlists with it. */
    suspend fun renameCategory(categoryId: String, name: String): Category =
        Json.category(send(HttpMethod.Put, "api/categories/$categoryId", buildJsonObject { put("name", name) }))

    /** Removes an activity for the whole household, with everything rated, picked or made for it. */
    suspend fun removeCategory(categoryId: String) {
        send(HttpMethod.Delete, "api/categories/$categoryId", null)
    }

    /** The person's latest lists of albums to try; [category] is a category id, or "none" for Mainstream's. */
    suspend fun lists(personId: String, category: String, limit: Int = 1): List<PickList> =
        Json.lists(get("api/people/$personId/lists", "category" to category, "limit" to limit.toString()))

    /** The activity's song playlists, newest first. */
    suspend fun playlists(personId: String, category: String, limit: Int): List<Playlist> =
        Json.playlists(get("api/people/$personId/playlists", "category" to category, "limit" to limit.toString()))

    /** Puts the playlist in the person's streaming app as its activity's playlist [slot] (1 to 3). */
    suspend fun putPlaylistInSlot(personId: String, playlistId: String, slot: Int): Playlist =
        Json.playlist(send(HttpMethod.Put, "api/people/$personId/playlists/$playlistId/slot",
            buildJsonObject { put("slot", slot) }))

    /** Deletes the playlist here and from the person's streaming app, and says how that went. */
    suspend fun deletePlaylist(personId: String, playlistId: String): String =
        Json.status(send(HttpMethod.Delete, "api/people/$personId/playlists/$playlistId", null))

    /** Deletes a list of albums to try. */
    suspend fun deleteList(personId: String, listId: String) {
        send(HttpMethod.Delete, "api/people/$personId/lists/$listId", null)
    }

    /** Takes one album out of a list of albums to try; the list goes with its last. */
    suspend fun removePick(personId: String, listId: String, pickId: String) {
        send(HttpMethod.Delete, "api/people/$personId/lists/$listId/picks/$pickId", null)
    }

    /** Takes an album out of the person's library, with its ratings for every activity. */
    suspend fun removeFromLibrary(personId: String, albumId: String) {
        send(HttpMethod.Delete, "api/people/$personId/library/$albumId", null)
    }

    /** What the person loves or rates OK for the activity, loved first. */
    suspend fun library(personId: String, category: String): List<LibraryAlbum> =
        Json.library(get("api/people/$personId/library", "category" to category))

    /** What the person turned down for the activity, there or in Mainstream. */
    suspend fun disliked(personId: String, category: String): List<LibraryAlbum> =
        Json.library(get("api/people/$personId/library/disliked", "category" to category))

    /** Albums kept elsewhere that the person has not yet rated for the activity. */
    suspend fun toSort(personId: String, category: String): List<LibraryAlbum> =
        Json.library(get("api/people/$personId/library/to-sort", "category" to category))

    /** Albums in the person's streaming catalogue, with their rating of each for the activity. */
    suspend fun search(personId: String, query: String, category: String): List<Found> =
        Json.found(get("api/people/$personId/search", "q" to query, "category" to category))

    /** The artists the person rated for the activity: loved first, then OK, then those turned down. */
    suspend fun artists(personId: String, category: String): List<RatedArtist> =
        Json.artists(get("api/people/$personId/artists", "category" to category))

    /**
     * Rates an artist for the activity; null takes the rating away. Loving one leads Claude's picks to them and
     * artists like them; turning one down keeps everything of theirs out of the activity.
     */
    suspend fun rateArtist(personId: String, artist: String, category: String, rating: Rating?, comment: String? = null) {
        send(HttpMethod.Post, "api/people/$personId/artist-ratings", buildJsonObject {
            put("artist", artist)
            put("categoryId", category)
            put("rating", rating?.let { JsonPrimitive(it.value) } ?: JsonNull)
            comment?.let { put("comment", it) }
        })
    }

    /** The person's songs with a rating that applies to the activity, loved first. */
    suspend fun songs(personId: String, category: String): List<Track> =
        Json.songs(get("api/people/$personId/songs", "category" to category))

    /** Songs in the person's streaming catalogue, with their rating of each for the activity. */
    suspend fun searchSongs(personId: String, query: String, category: String): List<Track> =
        Json.songs(get("api/people/$personId/songs/search", "q" to query, "category" to category))

    /** Rates a song for the activity, adding it to the person's songs when new; null takes the rating away. */
    suspend fun rateSong(personId: String, track: Track, category: String, rating: Rating?, comment: String? = null) {
        send(HttpMethod.Post, "api/people/$personId/song-ratings", buildJsonObject {
            put("ref", track.ref)
            put("artist", track.artist)
            put("title", track.title)
            put("album", track.album)
            put("cover", track.cover?.let(::JsonPrimitive) ?: JsonNull)
            put("categoryId", category)
            put("rating", rating?.let { JsonPrimitive(it.value) } ?: JsonNull)
            comment?.let { put("comment", it) }
        })
    }

    /**
     * Rates any album for the activity, adding it to the library when new; a null [rating] takes the rating away.
     * [comment] is what they said about it, such as why it is not for them.
     */
    suspend fun rate(personId: String, albumRef: String, category: String, rating: Rating?, comment: String? = null) {
        send(HttpMethod.Post, "api/people/$personId/ratings", buildJsonObject {
            put("albumRef", albumRef)
            put("categoryId", category)
            put("rating", rating?.let { JsonPrimitive(it.value) } ?: JsonNull)
            comment?.let { put("comment", it) }
        })
    }

    suspend fun moreLikeThis(personId: String, artist: String, title: String, category: String): List<Candidate> =
        Json.candidates(get("api/people/$personId/more-like-this",
            "artist" to artist, "title" to title, "category" to category))

    /** What the person played over the last [days], all of it in Mainstream or from the activity's picks. */
    suspend fun listening(personId: String, category: String, days: Int): Listening =
        Json.listening(get("api/people/$personId/plays", "category" to category, "days" to days.toString()))

    /** The week's most played songs and albums, with the person's ratings, and their weekly chart playlist. */
    suspend fun charts(personId: String): Charts = Json.charts(get("api/people/$personId/charts"))

    /** Turns the person's weekly chart playlist on or off, and says which it now is. */
    suspend fun setChartPlaylist(personId: String, enabled: Boolean): Boolean =
        send(HttpMethod.Put, "api/people/$personId/chart-playlist", buildJsonObject { put("enabled", enabled) })
            .contains("true")

    suspend fun tracking(personId: String): Tracking = Json.tracking(get("api/people/$personId/play-tracking"))

    /** The person's own switch, which every activity without a setting of its own follows. */
    suspend fun setPlayCounting(personId: String, enabled: Boolean): Tracking =
        Json.tracking(send(HttpMethod.Put, "api/people/$personId/play-tracking", buildJsonObject {
            put("categoryId", JsonNull)
            put("enabled", enabled)
        }))

    /** The sign-in page for the person's own service, or for [service] when they connect a second one. */
    suspend fun connectUrl(personId: String, service: String? = null): String =
        Json.connectUrl(get("api/people/$personId/connect", *listOfNotNull(service?.let { "service" to it }).toTypedArray()))

    /** Where each of the person's activities has its newest playlist copied. */
    suspend fun playlistCopies(personId: String): List<PlaylistCopy> =
        Json.copies(get("api/people/$personId/playlist-copies"))

    /**
     * Copies the activity's newest playlist to [service] from now on, or stops when it is null; [categoryId] null is
     * Mainstream. Says how the first copy went.
     */
    suspend fun copyPlaylists(personId: String, categoryId: String?, service: String?): String =
        Json.status(send(HttpMethod.Put, "api/people/$personId/playlist-copy",
            buildJsonObject { put("service", service) }, "category" to (categoryId ?: MAINSTREAM)))

    private suspend fun get(path: String, vararg query: Pair<String, String>): String =
        send(HttpMethod.Get, path, null, *query)

    private suspend fun send(
        method: HttpMethod,
        path: String,
        body: JsonObject?,
        vararg query: Pair<String, String>,
    ): String {
        val server = settings()
        val url = try {
            URLBuilder().takeFrom(server.serverUrl).appendPathSegments(path).apply {
                query.forEach { (name, value) -> parameters.append(name, value) }
            }.build()
        } catch (e: URLParserException) {
            throw notAWebAddress(server)
        } catch (e: IllegalArgumentException) {
            throw notAWebAddress(server)
        }
        val response = try {
            http.request(url) {
                this.method = method
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                header(HttpHeaders.Authorization, basic(server.username, server.password))
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }
        } catch (e: IOException) {
            throw ApiException("Can't reach the server at ${server.serverUrl}. Is Tailscale on?")
        }
        val status = response.status.value
        val text = response.bodyAsText()
        if (status == 401) throw ApiException("The server did not accept the login; check Settings", unauthorized = true)
        if (status >= 400) throw ApiException(Json.errorMessage(text) ?: "The server answered $status")
        return text
    }

    private fun notAWebAddress(server: ServerSettings) =
        ApiException("The server address \"${server.serverUrl}\" is not a web address; check Settings")

    companion object {
        fun basic(username: String, password: String): String =
            "Basic " + Base64.encode("$username:$password".encodeToByteArray())
    }
}
