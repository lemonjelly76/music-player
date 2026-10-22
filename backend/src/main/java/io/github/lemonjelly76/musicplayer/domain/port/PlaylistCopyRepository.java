package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistCopy;

import java.util.List;
import java.util.Optional;

/**
 * At most one copy per person and activity; no activity means General.
 */
public interface PlaylistCopyRepository {

    Optional<PlaylistCopy> find(PersonId person, Optional<CategoryId> activity);

    List<PlaylistCopy> forPerson(PersonId person);

    List<PlaylistCopy> forActivity(CategoryId activity);

    /**
     * The copy written as this playlist on its service, so plays from it count for its activity.
     */
    Optional<PlaylistCopy> findByPlaylist(String providerPlaylistId);

    void save(PlaylistCopy copy);

    void delete(PersonId person, Optional<CategoryId> activity);
}
