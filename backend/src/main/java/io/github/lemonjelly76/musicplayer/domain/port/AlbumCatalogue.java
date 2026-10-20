package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.util.Optional;

/**
 * A neutral catalogue (MusicBrainz) that says whether an album exists at all, whichever service plays it.
 */
public interface AlbumCatalogue {

    Optional<AlbumRef> findAlbum(String artist, String title);

    Optional<AlbumRef> byMusicBrainzId(String mbid);
}
