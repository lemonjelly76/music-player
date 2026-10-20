package io.github.lemonjelly76.musicplayer.domain.person;

import java.util.Objects;
import java.util.UUID;

public record PersonId(UUID value) {

    public PersonId {
        Objects.requireNonNull(value, "value");
    }

    public static PersonId random() {
        return new PersonId(UUID.randomUUID());
    }

    public static PersonId of(String text) {
        return new PersonId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
