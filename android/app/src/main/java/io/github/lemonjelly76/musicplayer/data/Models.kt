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

/** An album as the server describes it; [link] opens it in the person's streaming app when it carries it. */
data class Album(val ref: String, val artist: String, val title: String, val year: Int?, val link: String?)

enum class Verdict(val label: String) {
    LIKED("Liked"),
    NOT_FOR_ME("Not for me"),
    ALREADY_KNOW("Know it"),
}

data class Pick(
    val id: String,
    val album: Album,
    val reason: String,
    val wildcard: Boolean,
    val verdict: Verdict?,
    val comment: String?,
)

data class PickList(
    val id: String,
    val name: String,
    val categoryId: String?,
    val createdAt: String,
    val playlistLink: String?,
    val picks: List<Pick>,
)

data class LibraryAlbum(
    val id: String,
    val album: Album,
    val rating: Int?,
    val notes: String,
    val tags: List<String>,
    val addedOn: String,
)

data class Candidate(val album: Album, val score: Double, val sources: List<String>, val seeds: List<String>)

data class CategoryTracking(val categoryId: String, val name: String, val enabled: Boolean, val overridden: Boolean)

data class Tracking(val enabled: Boolean, val categories: List<CategoryTracking>)

/** Reads the server's JSON. Missing optional fields read as null, so older servers still parse. */
object Json {

    fun people(text: String): List<Person> = array(text).map {
        Person(
            id = it.getString("id"),
            name = it.getString("name"),
            service = it.optStringOrNull("service"),
            connected = it.optBoolean("connected"),
            playTracking = it.optBoolean("playTracking", true),
        )
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

    fun pick(text: String): Pick = pick(JSONObject(text))

    fun library(text: String): List<LibraryAlbum> = array(text).map(::libraryAlbum)

    fun libraryAlbum(text: String): LibraryAlbum = libraryAlbum(JSONObject(text))

    fun candidates(text: String): List<Candidate> = array(text).map {
        Candidate(
            album = album(it.getJSONObject("album")),
            score = it.optDouble("score"),
            sources = it.optJSONArray("sources").strings(),
            seeds = it.optJSONArray("seeds").strings(),
        )
    }

    fun tracking(text: String): Tracking {
        val json = JSONObject(text)
        return Tracking(
            enabled = json.getBoolean("enabled"),
            categories = json.optJSONArray("categories").objects().map {
                CategoryTracking(it.getString("categoryId"), it.getString("name"), it.getBoolean("enabled"),
                    it.optBoolean("overridden"))
            },
        )
    }

    fun connectUrl(text: String): String = JSONObject(text).getString("url")

    fun errorMessage(text: String): String? = runCatching { JSONObject(text).optStringOrNull("message") }.getOrNull()

    private fun pick(json: JSONObject) = Pick(
        id = json.getString("id"),
        album = album(json.getJSONObject("album")),
        reason = json.optString("reason"),
        wildcard = json.optString("source") == "WILDCARD",
        verdict = json.optStringOrNull("verdict")?.let { name -> Verdict.entries.firstOrNull { it.name == name } },
        comment = json.optStringOrNull("comment"),
    )

    private fun libraryAlbum(json: JSONObject) = LibraryAlbum(
        id = json.getString("id"),
        album = album(json.getJSONObject("album")),
        rating = if (json.isNull("rating") || !json.has("rating")) null else json.getInt("rating"),
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
    )

    private fun array(text: String): List<JSONObject> = JSONArray(text).objects()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name) || !has(name)) null else getString(name)
}
