package io.github.lemonjelly76.musicplayer.domain.playlist;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.Names;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;

import java.util.Objects;
import java.util.Optional;

/**
 * A song on one streaming service, with the album it is from; several artists are named together, comma separated.
 */
public record Track(Provider provider, String id, String artist, String title, String album, Optional<String> cover) {

    public Track {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(cover, "cover");
    }

    /**
     * The same song whatever its edition, so a playlist holds it once.
     */
    public String songKey() {
        return Names.normalise(artist) + "|" + Names.title(title);
    }

    public AlbumKey albumKey() {
        return AlbumKey.of(artist, album);
    }

    public String label() {
        return artist + " — " + title;
    }
}
