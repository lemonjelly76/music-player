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
 * ratings; they never talk to the model itself. [category] is always a category id, or [GENERAL]. The login is read
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

    /** The person's latest lists; [category] is a category id, or "none" for their general list. */
    suspend fun lists(personId: String, category: String, limit: Int = 1): List<PickList> =
        Json.lists(get("api/people/$personId/lists", "category" to category, "limit" to limit.toString()))

    /** What the person loves or rates OK for the activity, loved first. */
    suspend fun library(personId: String, category: String): List<LibraryAlbum> =
        Json.library(get("api/people/$personId/library", "category" to category))

    /** What the person turned down for the activity, there or in General. */
    suspend fun disliked(personId: String, category: String): List<LibraryAlbum> =
        Json.library(get("api/people/$personId/library/disliked", "category" to category))

    /** Albums kept elsewhere that the person has not yet rated for the activity. */
    suspend fun toSort(personId: String, category: String): List<LibraryAlbum> =
        Json.library(get("api/people/$personId/library/to-sort", "category" to category))

    /** Albums in the person's streaming catalogue, with their rating of each for the activity. */
    suspend fun search(personId: String, query: String, category: String): List<Found> =
        Json.found(get("api/people/$personId/search", "q" to query, "category" to category))

    /** Rates any album for the activity, adding it to the library when new; a null [rating] takes the rating away. */
    suspend fun rate(personId: String, albumRef: String, category: String, rating: Rating?) {
        send(HttpMethod.Post, "api/people/$personId/ratings", buildJsonObject {
            put("albumRef", albumRef)
            put("categoryId", category)
            put("rating", rating?.let { JsonPrimitive(it.value) } ?: JsonNull)
        })
    }

    suspend fun moreLikeThis(personId: String, artist: String, title: String, category: String): List<Candidate> =
        Json.candidates(get("api/people/$personId/more-like-this",
            "artist" to artist, "title" to title, "category" to category))

    suspend fun tracking(personId: String): Tracking = Json.tracking(get("api/people/$personId/play-tracking"))

    /** The person's own switch, which every activity without a setting of its own follows. */
    suspend fun setPlayCounting(personId: String, enabled: Boolean): Tracking =
        Json.tracking(send(HttpMethod.Put, "api/people/$personId/play-tracking", buildJsonObject {
            put("categoryId", JsonNull)
            put("enabled", enabled)
        }))

    suspend fun connectUrl(personId: String): String = Json.connectUrl(get("api/people/$personId/connect"))

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
