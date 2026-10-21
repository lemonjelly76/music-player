package io.github.lemonjelly76.musicplayer.ui

import io.github.lemonjelly76.musicplayer.data.LibraryAlbum
import io.github.lemonjelly76.musicplayer.data.Rating

/** Which of the activity's albums the library shows; [THIS_ACTIVITY] leaves out General ratings standing in. */
enum class LibraryFilter(val label: String, private val keeps: (LibraryAlbum) -> Boolean) {
    ALL("All", { true }),
    LOVED("Loved", { it.rating == Rating.LOVE }),
    OK("OK", { it.rating == Rating.OK }),
    THIS_ACTIVITY("Rated here", { !it.general });

    fun apply(library: List<LibraryAlbum>): List<LibraryAlbum> = library.filter(keeps)
}
