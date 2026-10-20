package io.github.lemonjelly76.musicplayer.domain.candidate;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.util.Objects;

/**
 * One similarity source saying "if you like the seed, try this album".
 */
public record Suggestion(AlbumRef album, String source, Seed seed) {

    public Suggestion {
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(seed, "seed");
    }
}
