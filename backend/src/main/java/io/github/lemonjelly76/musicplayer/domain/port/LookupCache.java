package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What each service answered when asked for a song or album by name, including that it had none, so the same
 * question need not be asked again. Keys are the names as asked, normalised.
 */
public interface LookupCache {

    Optional<Lookup<Track>> track(Provider provider, String key);

    void saveTrack(Provider provider, String key, Lookup<Track> lookup);

    Optional<Lookup<AlbumRef>> album(Provider provider, String key);

    void saveAlbum(Provider provider, String key, Lookup<AlbumRef> lookup);

    /**
     * The service's answer, empty when it did not carry it, and when it was given.
     */
    record Lookup<T>(Optional<T> found, Instant at) {

        public Lookup {
            Objects.requireNonNull(found, "found");
            Objects.requireNonNull(at, "at");
        }
    }
}
