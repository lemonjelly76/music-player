package io.github.lemonjelly76.musicplayer.ui

import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.Category
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Person
import io.github.lemonjelly76.musicplayer.data.Playlist
import io.github.lemonjelly76.musicplayer.data.Rating
import io.github.lemonjelly76.musicplayer.data.Service
import io.github.lemonjelly76.musicplayer.data.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeLogicTest {

    private fun entry(title: String, rating: Rating, general: Boolean = false) =
        LibraryAlbum(title, Album("tidal:$title", "Artist", title, null, null), rating, general, "", emptyList(), "")

    private val library = listOf(
        entry("Loved here", Rating.LOVE),
        entry("Loved in General", Rating.LOVE, general = true),
        entry("OK here", Rating.OK),
        entry("Not for me here", Rating.DISLIKE),
        entry("Not for me in General", Rating.DISLIKE, general = true),
    )

    @Test
    fun filtersTheLibraryByRating() {
        assertEquals(listOf("Loved here", "Loved in General", "OK here"), LibraryFilter.ALL.apply(library).map { it.album.title })
        assertEquals(listOf("Loved here", "Loved in General"), LibraryFilter.LOVED.apply(library).map { it.album.title })
        assertEquals(listOf("OK here"), LibraryFilter.OK.apply(library).map { it.album.title })
        assertEquals(listOf("Not for me here", "Not for me in General"),
            LibraryFilter.NOT_FOR_ME.apply(library).map { it.album.title })
    }

    @Test
    fun sortingGoesThroughTheAlbumsOnceCountingThoseRated() {
        val session = SortSession("c1", library.take(2))

        val afterLove = session.next(rated = true)
        val done = afterLove.next(rated = false)

        assertEquals("Loved here", session.current?.album?.title)
        assertEquals("Loved in General", afterLove.current?.album?.title)
        assertEquals(2, afterLove.number)
        assertTrue(done.finished)
        assertNull(done.current)
        assertEquals(1, done.rated)
    }

    @Test
    fun asksClaudeForAlbumsOrAPlaylistForThePersonAndActivity() {
        assertEquals("https://claude.ai/new?q=Give+Craig+10+new+albums+to+try+for+Running.",
            askClaudeForPicks("Craig", "Running"))
        assertEquals("https://claude.ai/new?q=Give+Craig+10+new+albums+to+try.", askClaudeForPicks("Craig", null))
        assertEquals("https://claude.ai/new?q=Make+Craig+a+50-song+playlist+for+Running.",
            askClaudeForPlaylist("Craig", "Running"))
    }

    @Test
    fun describesAPlaylistBySongsAndDate() {
        val songs = List(50) { Track("t$it", "Air", "Song $it", "Album", null, null) }
        val playlist = Playlist("p", "AI Picks", "", null, "2026-10-21T18:00:00Z", "https://open.spotify.com/playlist/p", songs)

        assertEquals("50 songs · 21 Oct 2026", playlistDetails(playlist))
        assertEquals("1 song · 21 Oct 2026", playlistDetails(playlist.copy(tracks = songs.take(1))))
    }

    @Test
    fun copiesPlaylistsToTheOtherServiceTheServerIsSetUpFor() {
        val services = listOf(Service("tidal", true), Service("spotify", true))
        val craig = Person("1", "Craig", "tidal", true, true)

        assertEquals("spotify", copyService(craig, services))
        assertEquals(null, copyService(craig, listOf(Service("tidal", true), Service("spotify", false))))
        assertEquals(null, copyService(craig.copy(service = null), services))
    }

    @Test
    fun namesEachSlotByWhatHoldsItNow() {
        fun playlist(id: String, date: String, slot: Int?, mood: String = "") =
            Playlist(id, "AI Picks", mood, null, "2026-10-${date}T18:00:00Z", null, emptyList(), slot)
        val shown = playlist("p1", "21", slot = 2, mood = "Bright and fast")
        val others = listOf(shown, playlist("p2", "20", slot = 1), playlist("p3", "19", slot = null))

        assertEquals(listOf("1 · 20 Oct", "2 · this one", "3 · empty"), slotChoices(shown, others))
        assertEquals(listOf("1 · 20 Oct", "2 · Bright and fast", "3 · empty"), slotChoices(others.last(), others))
        assertEquals("2 · A slow burn for late…", slotChoices(others.last(),
            listOf(shown.copy(description = "A slow burn for late nights in")))[1])
    }

    @Test
    fun writesAListsDateTheWayPeopleSayIt() {
        assertEquals("20 Oct 2026", shortDate("2026-10-20T18:00:00Z"))
        assertEquals("soon", shortDate("soon"))
    }

    @Test
    fun countsPlaysInWords() {
        assertEquals("1 play", playCount(1))
        assertEquals("12 plays", playCount(12))
    }

    @Test
    fun periodsReadAsPeopleSayThem() {
        assertEquals(listOf("4 weeks", "6 months", "1 year"), Period.entries.map { it.label })
        assertEquals("the last 4 weeks", Period.MONTH.since)
    }

    @Test
    fun filtersSongsByRatingAsAlbumsAre() {
        val loved = Track("t1", "Air", "Sexy Boy", "", null, null, rating = Rating.LOVE)
        val general = Track("t2", "Air", "All I Need", "", null, null, rating = Rating.OK, general = true)
        val turnedDown = Track("t3", "Air", "Talisman", "", null, null, rating = Rating.DISLIKE)
        val songs = listOf(loved, general, turnedDown)

        assertEquals(listOf(loved, general), LibraryFilter.ALL.applyToSongs(songs))
        assertEquals(listOf(loved), LibraryFilter.LOVED.applyToSongs(songs))
        assertEquals(listOf(turnedDown), LibraryFilter.NOT_FOR_ME.applyToSongs(songs))
    }

    @Test
    fun movesAnActivityStoppingAtEitherEnd() {
        val (running, dinner, chill) = listOf("Running", "Dinner", "Chill").map { Category(it.lowercase(), it, "") }
        val activities = listOf(running, dinner, chill)

        assertEquals(listOf(dinner, running, chill), activities.moved("dinner", -1))
        assertEquals(listOf(running, chill, dinner), activities.moved("dinner", 1))
        assertEquals(listOf(running, dinner, chill), activities.moved("chill", 1))
        assertEquals(activities, activities.moved("nope", 1))
    }

    @Test
    fun askingToDeleteAPlaylistSaysWhatGoesWhere() {
        fun playlist(id: String, slot: Int?) =
            Playlist(id, "AI Picks — Running", "", "c1", "2026-10-21T18:00:00Z", null, emptyList(), slot)
        val newest = playlist("new", 2)
        val older = playlist("old", null)

        assertEquals(Confirm("Delete this playlist?", "It goes from here and from Tidal, where it's AI Picks — Running 2.",
            "Delete"), deletePlaylistQuestion(newest, listOf(newest, older), "tidal", copiedTo = null))
        assertEquals("It isn't in Tidal, so it only goes from here.",
            deletePlaylistQuestion(older, listOf(newest, older), "tidal", copiedTo = "spotify").text)
        assertEquals("It goes from here and from Tidal, where it's AI Picks — Running 2. Its copy in Spotify becomes " +
            "the playlist before it.", deletePlaylistQuestion(newest, listOf(newest, older), "tidal", "spotify").text)
        assertEquals("It goes from here and from Tidal, where it's AI Picks — Running 2. Its copy in Spotify goes too.",
            deletePlaylistQuestion(newest, listOf(newest), "tidal", "spotify").text)
    }
}
