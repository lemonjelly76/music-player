package io.github.lemonjelly76.musicplayer.domain.library;

import java.util.Objects;
import java.util.UUID;

public record LibraryArtistId(UUID value) {

    public LibraryArtistId {
        Objects.requireNonNull(value, "value");
    }

    public static LibraryArtistId random() {
        return new LibraryArtistId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
