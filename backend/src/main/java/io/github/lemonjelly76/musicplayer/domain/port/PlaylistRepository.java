package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistId;

import java.util.List;
import java.util.Optional;

public interface PlaylistRepository {

    void save(Playlist playlist);

    Optional<Playlist> find(PlaylistId id);

    void delete(PlaylistId id);

    /**
     * The person's playlists for the activity that are in their account now, newest first.
     */
    List<Playlist> inAccount(PersonId person, Optional<CategoryId> activity);

    /**
     * The activity's playlists, newest first; no activity means the person's Mainstream ones only.
     */
    List<Playlist> latest(PersonId person, Optional<CategoryId> activity, int limit);

    /**
     * Everyone's playlists for the activity, newest first.
     */
    List<Playlist> forActivity(CategoryId activity);

    /**
     * The playlist written to the service under that id, so plays from it can be put down to its activity.
     */
    Optional<Playlist> findBySynced(String providerPlaylistId);
}
