package io.github.lemonjelly76.musicplayer.domain.library;

import java.util.Objects;
import java.util.UUID;

public record LibraryAlbumId(UUID value) {

    public LibraryAlbumId {
        Objects.requireNonNull(value, "value");
    }

    public static LibraryAlbumId random() {
        return new LibraryAlbumId(UUID.randomUUID());
    }

    public static LibraryAlbumId of(String text) {
        return new LibraryAlbumId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
