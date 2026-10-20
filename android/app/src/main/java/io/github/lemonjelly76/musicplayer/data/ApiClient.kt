package io.github.lemonjelly76.musicplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Base64

/** A failed server call; [unauthorized] when the server refused the saved login. */
class ApiException(message: String, val unauthorized: Boolean = false) : IOException(message)

/**
 * The server's REST API, signed in with the household login. The app only reads what the model wrote and records
 * feedback; it never talks to the model itself.
 */
class ApiClient(private val settings: ServerSettings) {

    suspend fun people(): List<Person> = Json.people(get("/api/people"))

    suspend fun services(): List<Service> = Json.services(get("/api/services"))

    suspend fun setService(personId: String, service: String): Person =
        Json.person(send("PUT", "/api/people/$personId/service", JSONObject().put("service", service)))

    suspend fun categories(): List<Category> = Json.categories(get("/api/categories"))

    /** The person's latest lists; [category] is a category id, or "none" for their general list. */
    suspend fun lists(personId: String, category: String, limit: Int = 1): List<PickList> =
        Json.lists(get("/api/people/$personId/lists?category=${encode(category)}&limit=$limit"))

    suspend fun feedback(pickId: String, verdict: Verdict, comment: String = ""): Pick =
        Json.pick(send("POST", "/api/picks/$pickId/feedback",
            JSONObject().put("verdict", verdict.name).put("comment", comment)))

    suspend fun library(personId: String): List<LibraryAlbum> = Json.library(get("/api/people/$personId/library"))

    suspend fun addToLibrary(personId: String, albumRef: String): LibraryAlbum =
        Json.libraryAlbum(send("POST", "/api/people/$personId/library", JSONObject().put("albumRef", albumRef)))

    suspend fun moreLikeThis(personId: String, artist: String, title: String): List<Candidate> =
        Json.candidates(get("/api/people/$personId/more-like-this?artist=${encode(artist)}&title=${encode(title)}"))

    suspend fun tracking(personId: String): Tracking = Json.tracking(get("/api/people/$personId/play-tracking"))

    /** A null [enabled] makes a category follow the person's own setting again. */
    suspend fun setTracking(personId: String, categoryId: String?, enabled: Boolean?): Tracking =
        Json.tracking(send("PUT", "/api/people/$personId/play-tracking",
            JSONObject().put("categoryId", categoryId ?: JSONObject.NULL).put("enabled", enabled ?: JSONObject.NULL)))

    suspend fun connectUrl(personId: String): String = Json.connectUrl(get("/api/people/$personId/connect"))

    private suspend fun get(path: String): String = send("GET", path, null)

    private suspend fun send(method: String, path: String, body: JSONObject?): String = withContext(Dispatchers.IO) {
        try {
            exchange(method, path, body)
        } catch (e: ApiException) {
            throw e
        } catch (e: MalformedURLException) {
            throw ApiException("The server address \"${settings.serverUrl}\" is not a web address; check Settings")
        } catch (e: IOException) {
            throw ApiException(unreachable(e))
        }
    }

    private fun exchange(method: String, path: String, body: JSONObject?): String {
        val connection = (URL(settings.serverUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", basic(settings.username, settings.password))
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status < 400) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status == 401) throw ApiException("The server did not accept the login; check Settings", unauthorized = true)
            if (status >= 400) throw ApiException(Json.errorMessage(text) ?: "The server answered $status")
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun unreachable(e: IOException): String = when (e) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "Can't reach the server at ${settings.serverUrl}. Is Tailscale on?"
        else -> e.message ?: "Can't reach the server"
    }

    companion object {
        fun basic(username: String, password: String): String =
            "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

        private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
    }
}
