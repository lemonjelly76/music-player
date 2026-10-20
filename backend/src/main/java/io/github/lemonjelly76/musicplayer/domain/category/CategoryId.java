package io.github.lemonjelly76.musicplayer.domain.category;

import java.util.Objects;
import java.util.UUID;

public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "value");
    }

    public static CategoryId random() {
        return new CategoryId(UUID.randomUUID());
    }

    public static CategoryId of(String text) {
        return new CategoryId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
