package io.github.lemonjelly76.musicplayer.domain.candidate;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.util.Objects;

/**
 * An album the search for new music starts from: one the person loves for the activity, with how often they play it.
 */
public record Seed(AlbumRef album, int plays) {

    public Seed {
        Objects.requireNonNull(album, "album");
        if (plays < 0) {
            throw new IllegalArgumentException("plays cannot be negative");
        }
    }

    public static Seed of(AlbumRef album) {
        return new Seed(album, 0);
    }
}
