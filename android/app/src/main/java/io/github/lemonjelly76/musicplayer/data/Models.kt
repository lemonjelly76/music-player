package io.github.lemonjelly76.musicplayer.data

import org.json.JSONArray
import org.json.JSONObject

data class Person(
    val id: String,
    val name: String,
    val service: String?,
    val connected: Boolean,
    val playTracking: Boolean,
)

data class Category(val id: String, val name: String, val rules: String)

/** Stands for General, the person's activity-free picks and ratings, which the server files under no category. */
const val GENERAL = "none"

/** A streaming service and whether the server is registered with it, so people can connect it. */
data class Service(val name: String, val configured: Boolean) {
    val label: String get() = serviceLabel(name)
}

/** The one place a service's display name comes from: "tidal" reads "Tidal". */
fun serviceLabel(name: String): String = when (name) {
    "tidal" -> "Tidal"
    "spotify" -> "Spotify"
    else -> name.replaceFirstChar { it.uppercase() }
}

/** The service a link opens in, for button labels such as "Open in Spotify". */
fun serviceOfLink(link: String): String = when {
    "tidal.com" in link -> serviceLabel("tidal")
    "spotify.com" in link -> serviceLabel("spotify")
    else -> "app"
}

/**
 * An album as the server describes it; [link] opens it in the person's streaming app when it carries it, and [cover]
 * is its artwork when a catalogue had some.
 */
data class Album(
    val ref: String,
    val artist: String,
    val title: String,
    val year: Int?,
    val link: String?,
    val cover: String? = null,
)

/** How much a person likes an album for one activity, 1 to 3 on the server (ADR-015). */
enum class Rating(val value: Int, val label: String) {
    DISLIKE(1, "Not for me"),
    OK(2, "OK"),
    LOVE(3, "Love");

    companion object {
        fun of(value: Int): Rating? = entries.firstOrNull { it.value == value }
    }
}

/** A pick with the person's [rating] of it for the list's activity, once they have given one. */
data class Pick(
    val id: String,
    val album: Album,
    val reason: String,
    val wildcard: Boolean,
    val rating: Rating?,
)

data class PickList(
    val id: String,
    val name: String,
    val categoryId: String?,
    val createdAt: String,
    val playlistLink: String?,
    val picks: List<Pick>,
)

/**
 * An album in the person's library for one activity, with its [rating] there; [general] when that is the General
 * rating standing in because the activity has none of its own.
 */
data class LibraryAlbum(
    val id: String,
    val album: Album,
    val rating: Rating?,
    val general: Boolean,
    val notes: String,
    val tags: List<String>,
    val addedOn: String,
)

data class Candidate(val album: Album, val score: Double, val sources: List<String>, val seeds: List<String>)

/** Whether the person's plays are counted; per-activity settings are Claude's to manage, so the app ignores them. */
data class Tracking(val enabled: Boolean)

/** Reads the server's JSON. Missing optional fields read as null, so older servers still parse. */
object Json {

    fun people(text: String): List<Person> = array(text).map(::person)

    fun person(text: String): Person = person(JSONObject(text))

    fun services(text: String): List<Service> = array(text).map {
        Service(it.getString("service"), it.optBoolean("configured"))
    }

    fun categories(text: String): List<Category> = array(text).map {
        Category(it.getString("id"), it.getString("name"), it.optString("rules"))
    }

    fun lists(text: String): List<PickList> = array(text).map { list ->
        PickList(
            id = list.getString("id"),
            name = list.getString("name"),
            categoryId = list.optStringOrNull("categoryId"),
            createdAt = list.optString("createdAt"),
            playlistLink = list.optStringOrNull("playlistLink"),
            picks = list.optJSONArray("picks").objects().map(::pick),
        )
    }

    fun library(text: String): List<LibraryAlbum> = array(text).map(::libraryAlbum)

    fun candidates(text: String): List<Candidate> = array(text).map {
        Candidate(
            album = album(it.getJSONObject("album")),
            score = it.optDouble("score"),
            sources = it.optJSONArray("sources").strings(),
            seeds = it.optJSONArray("seeds").strings(),
        )
    }

    fun tracking(text: String): Tracking = Tracking(enabled = JSONObject(text).getBoolean("enabled"))

    fun connectUrl(text: String): String = JSONObject(text).getString("url")

    fun errorMessage(text: String): String? = runCatching { JSONObject(text).optStringOrNull("message") }.getOrNull()

    private fun person(json: JSONObject) = Person(
        id = json.getString("id"),
        name = json.getString("name"),
        service = json.optStringOrNull("service"),
        connected = json.optBoolean("connected"),
        playTracking = json.optBoolean("playTracking", true),
    )

    private fun pick(json: JSONObject) = Pick(
        id = json.getString("id"),
        album = album(json.getJSONObject("album")),
        reason = json.optString("reason"),
        wildcard = json.optString("source") == "WILDCARD",
        rating = json.optRating(),
    )

    private fun libraryAlbum(json: JSONObject) = LibraryAlbum(
        id = json.getString("id"),
        album = album(json.getJSONObject("album")),
        rating = json.optRating(),
        general = json.optBoolean("general"),
        notes = json.optString("notes"),
        tags = json.optJSONArray("tags").strings(),
        addedOn = json.optString("addedOn"),
    )

    private fun album(json: JSONObject) = Album(
        ref = json.getString("ref"),
        artist = json.getString("artist"),
        title = json.getString("title"),
        year = if (json.isNull("year") || !json.has("year")) null else json.getInt("year"),
        link = json.optStringOrNull("link"),
        cover = json.optStringOrNull("cover"),
    )

    private fun array(text: String): List<JSONObject> = JSONArray(text).objects()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun JSONObject.optRating(): Rating? =
        if (isNull("rating") || !has("rating")) null else Rating.of(getInt("rating"))

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name) || !has(name)) null else getString(name)
}
