package io.github.lemonjelly76.musicplayer.domain.album;

import java.util.Arrays;

/**
 * The streaming services that hold a person's playlists and favourites and play the albums.
 */
public enum Provider {
    TIDAL("tidal"),
    SPOTIFY("spotify");

    private final String prefix;

    Provider(String prefix) {
        this.prefix = prefix;
    }

    /**
     * The lower-case name used in album references such as {@code tidal:12345}.
     */
    public String prefix() {
        return prefix;
    }

    public static Provider parse(String text) {
        return Arrays.stream(values())
                .filter(provider -> provider.prefix.equalsIgnoreCase(text.strip()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider '" + text + "'; use tidal or spotify"));
    }
}
