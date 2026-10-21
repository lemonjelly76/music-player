package io.github.lemonjelly76.musicplayer.ui

import io.github.lemonjelly76.musicplayer.data.LibraryAlbum

/**
 * Going through albums one at a time to rate each for the activity [category]; [rated] counts those not skipped.
 * The albums are fixed when it starts, so rating one does not reshuffle the rest.
 */
data class SortSession(
    val category: String,
    val albums: List<LibraryAlbum>,
    private val position: Int = 0,
    val rated: Int = 0,
) {
    val current: LibraryAlbum? get() = albums.getOrNull(position)

    /** The showing album's place, counting from one. */
    val number: Int get() = position + 1

    val finished: Boolean get() = position >= albums.size

    fun next(rated: Boolean): SortSession =
        copy(position = position + 1, rated = if (rated) this.rated + 1 else this.rated)
}
