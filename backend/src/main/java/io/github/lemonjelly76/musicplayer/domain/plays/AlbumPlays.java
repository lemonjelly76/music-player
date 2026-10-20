package io.github.lemonjelly76.musicplayer.domain.plays;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.time.Instant;
import java.util.Objects;

/**
 * How many tracks a person has played from an album, and when they last did.
 */
public record AlbumPlays(AlbumRef album, int plays, Instant lastPlayed) {

    public AlbumPlays {
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(lastPlayed, "lastPlayed");
    }
}
