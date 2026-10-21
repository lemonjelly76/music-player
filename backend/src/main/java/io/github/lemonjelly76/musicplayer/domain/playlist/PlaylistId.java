package io.github.lemonjelly76.musicplayer.domain.playlist;

import java.util.Objects;
import java.util.UUID;

public record PlaylistId(UUID value) {

    public PlaylistId {
        Objects.requireNonNull(value, "value");
    }

    public static PlaylistId random() {
        return new PlaylistId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
