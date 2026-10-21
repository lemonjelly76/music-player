package io.github.lemonjelly76.musicplayer.ui

import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Playlist
import io.github.lemonjelly76.musicplayer.data.Rating
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
    fun filtersTheLibraryByRatingOrToTheActivitysOwnRatings() {
        assertEquals(listOf("Loved here", "Loved in General", "OK here"), LibraryFilter.ALL.apply(library).map { it.album.title })
        assertEquals(listOf("Loved here", "Loved in General"), LibraryFilter.LOVED.apply(library).map { it.album.title })
        assertEquals(listOf("OK here"), LibraryFilter.OK.apply(library).map { it.album.title })
        assertEquals(listOf("Loved here", "OK here"), LibraryFilter.THIS_ACTIVITY.apply(library).map { it.album.title })
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
    fun describesAPlaylistBySongsAndWhetherItWasReplaced() {
        val songs = List(50) { Track("t$it", "Air", "Song $it", "Album", null, null) }
        val playlist = Playlist("p", "AI Picks", "", null, "2026-10-21T18:00:00Z", "https://open.spotify.com/playlist/p", songs)

        assertEquals("50 songs · 21 Oct 2026", playlistDetails(playlist))
        assertEquals("1 song · 21 Oct 2026 · replaced since",
            playlistDetails(playlist.copy(link = null, tracks = songs.take(1))))
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
}
