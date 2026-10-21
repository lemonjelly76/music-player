package io.github.lemonjelly76.musicplayer.ui

import io.github.lemonjelly76.musicplayer.data.Album
import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Rating
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLogicTest {

    private fun entry(title: String, rating: Rating, general: Boolean = false) =
        LibraryAlbum(title, Album("tidal:$title", "Artist", title, null, null), rating, general, "", emptyList(), "")

    private val library = listOf(
        entry("Loved here", Rating.LOVE),
        entry("Loved in General", Rating.LOVE, general = true),
        entry("OK here", Rating.OK),
    )

    @Test
    fun filtersTheLibraryByRatingOrToTheActivitysOwnRatings() {
        assertEquals(3, LibraryFilter.ALL.apply(library).size)
        assertEquals(listOf("Loved here", "Loved in General"), LibraryFilter.LOVED.apply(library).map { it.album.title })
        assertEquals(listOf("OK here"), LibraryFilter.OK.apply(library).map { it.album.title })
        assertEquals(listOf("Loved here", "OK here"), LibraryFilter.THIS_ACTIVITY.apply(library).map { it.album.title })
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
