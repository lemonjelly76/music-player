package io.github.lemonjelly76.musicplayer.domain.plays;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One track played from an album, and the playlist it was played from when the listening history says.
 */
public record Play(AlbumRef album, Instant playedAt, String source, Optional<String> playlistId) {

    public Play {
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(playedAt, "playedAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(playlistId, "playlistId");
    }
}
