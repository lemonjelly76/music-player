package io.github.lemonjelly76.musicplayer.domain.library;

import java.util.Objects;
import java.util.UUID;

public record LibrarySongId(UUID value) {

    public LibrarySongId {
        Objects.requireNonNull(value, "value");
    }

    public static LibrarySongId random() {
        return new LibrarySongId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
