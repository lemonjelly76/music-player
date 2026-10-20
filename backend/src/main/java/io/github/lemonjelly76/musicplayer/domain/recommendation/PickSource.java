package io.github.lemonjelly76.musicplayer.domain.recommendation;

import java.util.Locale;

/**
 * Where a pick came from: the server's candidate pool, or the model's own knowledge.
 */
public enum PickSource {
    POOL,
    WILDCARD;

    public static PickSource parse(String text) {
        try {
            return valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown source '" + text + "'; use pool or wildcard", e);
        }
    }
}
