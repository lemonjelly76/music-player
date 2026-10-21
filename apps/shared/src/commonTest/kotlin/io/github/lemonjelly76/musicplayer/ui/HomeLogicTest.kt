package io.github.lemonjelly76.musicplayer.ui

import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Rating
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
    fun asksClaudeForPicksForThePersonAndActivity() {
        assertEquals("https://claude.ai/new?q=Give+Craig+10+new+album+picks+for+Running.",
            askClaudeForPicks("Craig", "Running"))
        assertEquals("https://claude.ai/new?q=Give+Craig+10+new+album+picks.", askClaudeForPicks("Craig", null))
    }

    @Test
    fun writesAListsDateTheWayPeopleSayIt() {
        assertEquals("20 Oct 2026", shortDate("2026-10-20T18:00:00Z"))
        assertEquals("soon", shortDate("soon"))
    }
}
