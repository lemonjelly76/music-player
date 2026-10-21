package io.github.lemonjelly76.musicplayer.domain.chart;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A chart found on one streaming service: each song it carries, and each album, with the service's id where it has
 * it. Songs the service does not carry are left out, as they cannot be played.
 */
public record ResolvedChart(Provider provider, String country, Instant fetchedAt, List<Song> songs, List<Album> albums) {

    public ResolvedChart {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        songs = List.copyOf(songs);
        albums = List.copyOf(albums);
    }

    public record Song(int position, Track track) {
    }

    public record Album(int position, AlbumRef album) {
    }
}
