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
    /** Services other than their own they connected too, as to copy playlists there. */
    val alsoConnected: List<String> = emptyList(),
)

/** Where an album or song opens on a service other than the person's own. */
data class ServiceLink(val service: String, val link: String) {
    val label: String get() = "Open in ${serviceLabel(service)}"
}

/** Where an activity's newest playlist is copied ([categoryId] null is Mainstream); [link] once it is there. */
data class PlaylistCopy(val categoryId: String?, val service: String, val link: String?)

/**
 * The badges for the qualities beyond the ordinary something comes in. Lossless is left out, as nearly everything on
 * Tidal is.
 */
fun qualityBadges(formats: List<String>): List<String> = formats.mapNotNull {
    when (it) {
        "HI_RES" -> "Hi-Res"
        "DOLBY_ATMOS" -> "Atmos"
        else -> null
    }
}

data class Category(val id: String, val name: String, val rules: String)

/** Stands for Mainstream, the person's activity-free picks and ratings, which the server files under no category. */
const val MAINSTREAM = "none"

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
    /** Qualities beyond the ordinary it comes in, as the server names them: LOSSLESS, HI_RES, DOLBY_ATMOS. */
    val formats: List<String> = emptyList(),
    val elsewhere: List<ServiceLink> = emptyList(),
) {
    val qualities: List<String> get() = qualityBadges(formats)
}

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

/** Albums Claude picked to try, with a reason for each; they are opened one by one, not played as a playlist. */
data class PickList(
    val id: String,
    val name: String,
    val categoryId: String?,
    val createdAt: String,
    val picks: List<Pick>,
)

/**
 * A song; [link] plays it in the person's streaming app, and [ref] (such as spotify:<id>) names it to the server.
 * [rating] is the person's for the activity it is shown for, when it has one there.
 */
data class Track(
    val id: String,
    val artist: String,
    val title: String,
    val album: String,
    val cover: String?,
    val link: String?,
    val ref: String = id,
    val rating: Rating? = null,
    val formats: List<String> = emptyList(),
    val elsewhere: List<ServiceLink> = emptyList(),
) {
    val qualities: List<String> get() = qualityBadges(formats)
}

/**
 * Songs Claude picked to play straight through. Only the newest for an activity has a [link], as each new one
 * replaces it in the person's account.
 */
data class Playlist(
    val id: String,
    val name: String,
    val description: String,
    val categoryId: String?,
    val createdAt: String,
    val link: String?,
    val tracks: List<Track>,
    /** Its number among the activity's playlists in the person's streaming app, 1 to [SLOTS]; null when not there. */
    val slot: Int? = null,
) {
    /** One cover per album, as albums, for the covers a playlist is shown with. */
    val covers: List<Album>
        get() = tracks.filter { it.cover != null }.distinctBy { it.cover }
            .map { Album(it.id, it.artist, it.album, null, null, it.cover) }

    companion object {
        /** How many songs Claude puts in a playlist. */
        const val SIZE = 50

        /** How many of an activity's playlists are kept in the person's streaming app. */
        const val SLOTS = 3
    }
}

/**
 * An album in the person's library, with its [rating] for the activity shown, when it has one there.
 */
data class LibraryAlbum(
    val id: String,
    val album: Album,
    val rating: Rating?,
    val notes: String,
    val tags: List<String>,
    val addedOn: String,
)

/** A catalogue search result with the person's [rating] of it for the activity, when it is in their library. */
data class Found(val album: Album, val rating: Rating?)

data class Candidate(val album: Album, val score: Double, val sources: List<String>, val seeds: List<String>)

/** An album the person played [plays] tracks from. */
data class PlayedAlbum(val album: Album, val plays: Int)

data class ArtistPlays(val name: String, val plays: Int, val albums: Int)

/**
 * What the person played over the last [days]: [tracks] across [albumCount] albums, and the most played. Nothing is
 * there when [tracking] is off.
 */
data class Listening(
    val tracking: Boolean,
    val days: Int,
    val tracks: Int,
    val albumCount: Int,
    val albums: List<PlayedAlbum>,
    val artists: List<ArtistPlays>,
)

data class ChartSong(val position: Int, val song: Track)

data class ChartAlbum(val position: Int, val album: Found)

/**
 * The week's most played songs and albums at their chart places, with the person's Mainstream rating of each, and
 * their weekly chart playlist. [updatedAt] is null, and the lists empty, until the server first fetches the chart.
 */
data class Charts(
    val country: String?,
    val updatedAt: String?,
    val playlistWanted: Boolean,
    val playlistLink: String?,
    val songs: List<ChartSong>,
    val albums: List<ChartAlbum>,
) {
    val covers: List<Album>
        get() = songs.map { it.song }.filter { it.cover != null }.distinctBy { it.cover }
            .map { Album(it.ref, it.artist, it.album, null, null, it.cover) }
}

/** Whether the person's plays are counted; per-activity settings are Claude's to manage, so the app ignores them. */
data class Tracking(val enabled: Boolean)

/** Reads the server's JSON. Missing optional fields read as null, so older servers still parse. */
object Json {

    fun people(text: String): List<Person> = array(text).map(::person)

    fun person(text: String): Person = person(obj(text))

    fun services(text: String): List<Service> = array(text).map {
        Service(it.string("service"), it.boolean("configured"))
    }

    fun categories(text: String): List<Category> = array(text).map(::category)

    fun category(text: String): Category = category(obj(text))

    private fun category(json: JsonObject) = Category(json.string("id"), json.string("name"), json.optString("rules"))

    fun lists(text: String): List<PickList> = array(text).map { list ->
        PickList(
            id = list.string("id"),
            name = list.string("name"),
            categoryId = list.stringOrNull("categoryId"),
            createdAt = list.optString("createdAt"),
            picks = list.objects("picks").map(::pick),
        )
    }

    fun playlists(text: String): List<Playlist> = array(text).map(::playlist)

    fun playlist(text: String): Playlist = playlist(obj(text))

    private fun playlist(json: JsonObject) = Playlist(
        id = json.string("id"),
        name = json.string("name"),
        description = json.optString("description"),
        categoryId = json.stringOrNull("categoryId"),
        createdAt = json.optString("createdAt"),
        link = json.stringOrNull("link"),
        tracks = json.objects("tracks").map(::track),
        slot = json.primitive("slot")?.intOrNull,
    )

    fun songs(text: String): List<Track> = array(text).map(::track)

    fun charts(text: String): Charts = obj(text).let { json ->
        Charts(
            country = json.stringOrNull("country"),
            updatedAt = json.stringOrNull("updatedAt"),
            playlistWanted = json.boolean("playlistWanted", true),
            playlistLink = json.stringOrNull("playlistLink"),
            songs = json.objects("songs").map { ChartSong(it.int("position"), track(it.obj("song"))) },
            albums = json.objects("albums").map { ChartAlbum(it.int("position"), found(it.obj("album"))) },
        )
    }

    fun library(text: String): List<LibraryAlbum> = array(text).map(::libraryAlbum)

    fun found(text: String): List<Found> = array(text).map(::found)

    private fun found(json: JsonObject) = Found(album(json.obj("album")), json.rating())

    fun candidates(text: String): List<Candidate> = array(text).map {
        Candidate(
            album = album(it.obj("album")),
            score = it.primitive("score")?.doubleOrNull ?: Double.NaN,
            sources = it.strings("sources"),
            seeds = it.strings("seeds"),
        )
    }

    fun listening(text: String): Listening = obj(text).let { json ->
        Listening(
            tracking = json.boolean("tracking"),
            days = json.int("days"),
            tracks = json.int("tracks"),
            albumCount = json.int("albumCount"),
            albums = json.objects("albums").map { PlayedAlbum(album(it.obj("album")), it.int("plays")) },
            artists = json.objects("artists").map { ArtistPlays(it.string("name"), it.int("plays"), it.int("albums")) },
        )
    }

    fun tracking(text: String): Tracking = Tracking(enabled = obj(text).getValue("enabled").jsonPrimitive.boolean)

    fun connectUrl(text: String): String = obj(text).string("url")

    fun copies(text: String): List<PlaylistCopy> = array(text).map {
        PlaylistCopy(it.stringOrNull("categoryId"), it.string("service"), it.stringOrNull("link"))
    }

    fun status(text: String): String = obj(text).optString("status")

    fun errorMessage(text: String): String? = runCatching { obj(text).stringOrNull("message") }.getOrNull()

    private fun person(json: JsonObject) = Person(
        id = json.string("id"),
        name = json.string("name"),
        service = json.stringOrNull("service"),
        connected = json.boolean("connected"),
        playTracking = json.boolean("playTracking", true),
        alsoConnected = json.strings("alsoConnected"),
    )

    private fun track(json: JsonObject): Track {
        val id = json.string("id")
        return Track(id, json.string("artist"), json.string("title"), json.optString("album"),
            json.stringOrNull("cover"), json.stringOrNull("link"), json.stringOrNull("ref") ?: id, json.rating(),
            json.strings("formats"), json.links())
    }

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
        formats = json.strings("formats"),
        elsewhere = json.links(),
    )

    private fun obj(text: String): JsonObject = parse(text).jsonObject

    private fun array(text: String): List<JsonObject> = parse(text).jsonArray.map { it.jsonObject }

    private fun parse(text: String): JsonElement = KotlinJson.parseToJsonElement(text)

    /** The field when it is there and not null. */
    private fun JsonObject.primitive(name: String): JsonPrimitive? = (get(name) as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    private fun JsonObject.stringOrNull(name: String): String? = primitive(name)?.content

    private fun JsonObject.int(name: String): Int = primitive(name)?.intOrNull ?: 0

    private fun JsonObject.optString(name: String): String = stringOrNull(name).orEmpty()

    private fun JsonObject.boolean(name: String, default: Boolean = false): Boolean =
        primitive(name)?.booleanOrNull ?: default

    private fun JsonObject.objects(name: String): List<JsonObject> =
        (get(name) as? JsonArray)?.map { it.jsonObject }.orEmpty()

    private fun JsonObject.strings(name: String): List<String> =
        (get(name) as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()

    private fun JsonObject.links(): List<ServiceLink> =
        objects("elsewhere").map { ServiceLink(it.string("service"), it.string("link")) }

    private fun JsonObject.rating(): Rating? = primitive("rating")?.intOrNull?.let(Rating::of)
}
