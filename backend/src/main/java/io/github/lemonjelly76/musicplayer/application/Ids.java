package io.github.lemonjelly76.musicplayer.application;

import java.util.Optional;
import java.util.UUID;

final class Ids {

    private Ids() {
    }

    static Optional<UUID> uuid(String text) {
        try {
            return Optional.of(UUID.fromString(text.strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
