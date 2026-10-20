package io.github.lemonjelly76.musicplayer.domain.recommendation;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum Verdict {
    LIKED,
    NOT_FOR_ME,
    ALREADY_KNOW;

    /**
     * Reads "liked", "not for me", "NOT_FOR_ME" or "already-know" alike.
     */
    public static Verdict parse(String text) {
        String name = text.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return Arrays.stream(values())
                .filter(verdict -> verdict.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown verdict '" + text + "'; use one of "
                        + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "))));
    }
}
