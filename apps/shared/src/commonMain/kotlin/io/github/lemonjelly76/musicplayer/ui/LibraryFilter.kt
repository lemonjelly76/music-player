package io.github.lemonjelly76.musicplayer.ui

import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Rating

/**
 * Which of the activity's albums the library shows; [THIS_ACTIVITY] leaves out General ratings standing in, and only
 * [NOT_FOR_ME] shows what the person turned down.
 */
enum class LibraryFilter(val label: String, private val keeps: (LibraryAlbum) -> Boolean) {
    ALL("All", { it.rating != Rating.DISLIKE }),
    LOVED("Loved", { it.rating == Rating.LOVE }),
    OK("OK", { it.rating == Rating.OK }),
    THIS_ACTIVITY("Rated here", { !it.general && it.rating != Rating.DISLIKE }),
    NOT_FOR_ME("Not for me", { it.rating == Rating.DISLIKE });

    fun apply(library: List<LibraryAlbum>): List<LibraryAlbum> = library.filter(keeps)
}
