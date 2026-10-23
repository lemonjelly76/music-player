package io.github.lemonjelly76.musicplayer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class TidalLinksTest {

    @Test
    fun offersTheTidalAppEachFormOfAnAlbumTrackOrPlaylistLink() {
        assertEquals(listOf("https://tidal.com/browse/album/77", "https://listen.tidal.com/album/77", "tidal://album/77"),
            tidalAppLinks("https://tidal.com/browse/album/77"))
        assertEquals("tidal://playlist/9a-b2", tidalAppLinks("https://tidal.com/browse/playlist/9a-b2").last())
        assertEquals("tidal://track/5", tidalAppLinks("https://tidal.com/browse/track/5").last())
    }

    @Test
    fun offersASearchAsItIsAndLeavesOtherLinksAlone() {
        assertEquals(listOf("https://tidal.com/browse/search?q=Air+Moon+Safari"),
            tidalAppLinks("https://tidal.com/browse/search?q=Air+Moon+Safari"))
        assertEquals(emptyList(), tidalAppLinks("https://open.spotify.com/album/1"))
    }
}
