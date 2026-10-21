package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * A streaming service as the catalogue and the place lists are played from. Albums it returns carry its own id.
 * Calls that act on a person's account need that person to have connected it; they fail with
 * {@link MusicProviderUnavailableException} otherwise.
 */
public interface MusicProvider {

    Provider name();

    /**
     * Whether the app is registered with this service, so it can be called at all.
     */
    boolean configured();

    boolean connected(PersonId person);

    List<AlbumRef> searchAlbums(String query, int limit);

    Optional<AlbumRef> album(String id);

    /**
     * The service's own album for an artist and title, matched loosely, or empty when it does not carry it.
     */
    Optional<AlbumRef> findAlbum(String artist, String title);

    List<AlbumRef> similarAlbums(String id, int limit);

    List<AlbumRef> favouriteAlbums(PersonId person);

    void addFavourite(PersonId person, String id);

    /**
     * Writes the albums, every track in order, to a playlist of that name in the person's account, replacing the
     * previous one when its id is given, and returns the new playlist's id.
     */
    String replacePlaylist(PersonId person, Optional<String> previousPlaylistId, String name, String description,
            List<String> albumIds);

    /**
     * A link that opens the album in the service's own app.
     */
    URI albumLink(String id);

    /**
     * A link that opens the service's own search, for an album whose id on the service is not known.
     */
    URI searchLink(String query);

    URI playlistLink(String playlistId);
}
