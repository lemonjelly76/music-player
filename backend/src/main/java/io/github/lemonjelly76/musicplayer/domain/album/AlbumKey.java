package io.github.lemonjelly76.musicplayer.domain.album;

import java.util.Objects;

/**
 * Identifies an album across catalogues by artist and title (see {@link Names}).
 */
public record AlbumKey(String value) {

    public AlbumKey {
        Objects.requireNonNull(value, "value");
    }

    public static AlbumKey of(String artist, String title) {
        return new AlbumKey(Names.normalise(artist) + "|" + Names.title(title));
    }

    @Override
    public String toString() {
        return value;
    }
}
