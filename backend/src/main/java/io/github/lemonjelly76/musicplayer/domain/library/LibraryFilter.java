package io.github.lemonjelly76.musicplayer.domain.library;

import java.util.Locale;
import java.util.Optional;

/**
 * Narrows a library by tag, decade (1990 for the nineties) and artist; empty parts match everything.
 */
public record LibraryFilter(Optional<String> tag, Optional<Integer> decade, Optional<String> artist) {

    public static final LibraryFilter ALL = new LibraryFilter(Optional.empty(), Optional.empty(), Optional.empty());

    public boolean matches(LibraryAlbum entry) {
        return tag.map(entry::hasTag).orElse(true)
                && decade.map(wanted -> entry.album().decade().map(wanted::equals).orElse(false)).orElse(true)
                && artist.map(name -> entry.album().artist().toLowerCase(Locale.ROOT)
                        .contains(name.strip().toLowerCase(Locale.ROOT))).orElse(true);
    }
}
