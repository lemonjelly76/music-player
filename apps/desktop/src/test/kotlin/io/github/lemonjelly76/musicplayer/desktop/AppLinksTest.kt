package io.github.lemonjelly76.musicplayer.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLinksTest {

    private val withSpotify = AppLinks(spotifyInstalled = { true })
    private val withoutSpotify = AppLinks(spotifyInstalled = { false })

    @Test
    fun opensSpotifyPagesInTheSpotifyApp() {
        assertEquals("spotify:album:4aawyAB9vmqN3uQ7FjRGTy",
            withSpotify.forThisComputer("https://open.spotify.com/album/4aawyAB9vmqN3uQ7FjRGTy"))
        assertEquals("spotify:track:7ouMYWpwJ422jRcDASZB7P",
            withSpotify.forThisComputer("https://open.spotify.com/track/7ouMYWpwJ422jRcDASZB7P?si=abc"))
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            withSpotify.forThisComputer("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertEquals("spotify:search:Talk%20Talk%20Spirit%20of%20Eden",
            withSpotify.forThisComputer("https://open.spotify.com/search/Talk%20Talk%20Spirit%20of%20Eden"))
    }

    @Test
    fun keepsTheWebPlayerWhenSpotifyIsNotInstalled() {
        val link = "https://open.spotify.com/album/4aawyAB9vmqN3uQ7FjRGTy"

        assertEquals(link, withoutSpotify.forThisComputer(link))
    }

    @Test
    fun leavesOtherLinksAlone() {
        listOf("https://tidal.com/browse/album/1", "https://open.spotify.com/", "https://example.com/album/1")
            .forEach { assertEquals(it, withSpotify.forThisComputer(it)) }
    }
}
