package io.github.lemonjelly76.musicplayer.ui

import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Rating
import io.github.lemonjelly76.musicplayer.data.Track

/**
 * Which of the activity's albums or songs the library shows, by their rating there and whether it is General's
 * standing in; [THIS_ACTIVITY] leaves those out, and only [NOT_FOR_ME] shows what the person turned down.
 */
enum class LibraryFilter(val label: String, private val keeps: (rating: Rating?, general: Boolean) -> Boolean) {
    ALL("All", { rating, _ -> rating != Rating.DISLIKE }),
    LOVED("Loved", { rating, _ -> rating == Rating.LOVE }),
    OK("OK", { rating, _ -> rating == Rating.OK }),
    THIS_ACTIVITY("Rated here", { rating, general -> !general && rating != Rating.DISLIKE }),
    NOT_FOR_ME("Not for me", { rating, _ -> rating == Rating.DISLIKE });

    fun apply(library: List<LibraryAlbum>): List<LibraryAlbum> = library.filter { keeps(it.rating, it.general) }

    fun applyToSongs(songs: List<Track>): List<Track> = songs.filter { keeps(it.rating, it.general) }
}
