package io.github.lemonjelly76.musicplayer.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json as KotlinJson

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

/** A catalogue search result with the person's [rating] of it for the activity, when it is in their library. */
data class Found(val album: Album, val rating: Rating?, val general: Boolean)

data class Candidate(val album: Album, val score: Double, val sources: List<String>, val seeds: List<String>)

/** Whether the person's plays are counted; per-activity settings are Claude's to manage, so the app ignores them. */
data class Tracking(val enabled: Boolean)

/** Reads the server's JSON. Missing optional fields read as null, so older servers still parse. */
object Json {

    fun people(text: String): List<Person> = array(text).map(::person)

    fun person(text: String): Person = person(obj(text))

    fun services(text: String): List<Service> = array(text).map {
        Service(it.string("service"), it.boolean("configured"))
    }

    fun categories(text: String): List<Category> = array(text).map {
        Category(it.string("id"), it.string("name"), it.optString("rules"))
    }

    fun lists(text: String): List<PickList> = array(text).map { list ->
        PickList(
            id = list.string("id"),
            name = list.string("name"),
            categoryId = list.stringOrNull("categoryId"),
            createdAt = list.optString("createdAt"),
            playlistLink = list.stringOrNull("playlistLink"),
            picks = list.objects("picks").map(::pick),
        )
    }

    fun library(text: String): List<LibraryAlbum> = array(text).map(::libraryAlbum)

    fun found(text: String): List<Found> = array(text).map {
        Found(album(it.obj("album")), it.rating(), it.boolean("general"))
    }

    fun candidates(text: String): List<Candidate> = array(text).map {
        Candidate(
            album = album(it.obj("album")),
            score = it.primitive("score")?.doubleOrNull ?: Double.NaN,
            sources = it.strings("sources"),
            seeds = it.strings("seeds"),
        )
    }

    fun tracking(text: String): Tracking = Tracking(enabled = obj(text).getValue("enabled").jsonPrimitive.boolean)

    fun connectUrl(text: String): String = obj(text).string("url")

    fun errorMessage(text: String): String? = runCatching { obj(text).stringOrNull("message") }.getOrNull()

    private fun person(json: JsonObject) = Person(
        id = json.string("id"),
        name = json.string("name"),
        service = json.stringOrNull("service"),
        connected = json.boolean("connected"),
        playTracking = json.boolean("playTracking", true),
    )

    private fun pick(json: JsonObject) = Pick(
        id = json.string("id"),
        album = album(json.obj("album")),
        reason = json.optString("reason"),
        wildcard = json.optString("source") == "WILDCARD",
        rating = json.rating(),
    )

    private fun libraryAlbum(json: JsonObject) = LibraryAlbum(
        id = json.string("id"),
        album = album(json.obj("album")),
        rating = json.rating(),
        general = json.boolean("general"),
        notes = json.optString("notes"),
        tags = json.strings("tags"),
        addedOn = json.optString("addedOn"),
    )

    private fun album(json: JsonObject) = Album(
        ref = json.string("ref"),
        artist = json.string("artist"),
        title = json.string("title"),
        year = json.primitive("year")?.intOrNull,
        link = json.stringOrNull("link"),
        cover = json.stringOrNull("cover"),
    )

    private fun obj(text: String): JsonObject = parse(text).jsonObject

    private fun array(text: String): List<JsonObject> = parse(text).jsonArray.map { it.jsonObject }

    private fun parse(text: String): JsonElement = KotlinJson.parseToJsonElement(text)

    /** The field when it is there and not null. */
    private fun JsonObject.primitive(name: String): JsonPrimitive? = (get(name) as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    private fun JsonObject.stringOrNull(name: String): String? = primitive(name)?.content

    private fun JsonObject.optString(name: String): String = stringOrNull(name).orEmpty()

    private fun JsonObject.boolean(name: String, default: Boolean = false): Boolean =
        primitive(name)?.booleanOrNull ?: default

    private fun JsonObject.objects(name: String): List<JsonObject> =
        (get(name) as? JsonArray)?.map { it.jsonObject }.orEmpty()

    private fun JsonObject.strings(name: String): List<String> =
        (get(name) as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()

    private fun JsonObject.rating(): Rating? = primitive("rating")?.intOrNull?.let(Rating::of)
}
