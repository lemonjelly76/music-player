package io.github.lemonjelly76.musicplayer.ui

import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Rating
import io.github.lemonjelly76.musicplayer.data.RatedArtist
import io.github.lemonjelly76.musicplayer.data.Track

/**
 * Which of the activity's albums, songs or artists the library shows, by their rating there; only [NOT_FOR_ME] shows what the
 * person turned down.
 */
enum class LibraryFilter(val label: String, private val keeps: (Rating?) -> Boolean) {
    ALL("All", { it != Rating.DISLIKE }),
    LOVED("Loved", { it == Rating.LOVE }),
    OK("OK", { it == Rating.OK }),
    NOT_FOR_ME("Not for me", { it == Rating.DISLIKE });

    fun apply(library: List<LibraryAlbum>): List<LibraryAlbum> = library.filter { keeps(it.rating) }

    fun applyToSongs(songs: List<Track>): List<Track> = songs.filter { keeps(it.rating) }

    fun applyToArtists(artists: List<RatedArtist>): List<RatedArtist> = artists.filter { keeps(it.rating) }
}
