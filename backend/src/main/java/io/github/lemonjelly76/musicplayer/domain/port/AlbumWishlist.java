package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

/**
 * Somewhere albums can be asked for, so they get downloaded and kept: Lidarr.
 */
public interface AlbumWishlist {

    boolean configured();

    /**
     * Adds the album as wanted and returns a short description of what happened.
     */
    String want(AlbumRef album);
}
