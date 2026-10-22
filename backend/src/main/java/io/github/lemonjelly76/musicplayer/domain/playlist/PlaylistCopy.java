package io.github.lemonjelly76.musicplayer.domain.playlist;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.util.Objects;
import java.util.Optional;

/**
 * A person's wish to keep an activity's newest playlist on a second service too, named for the activity without a
 * number ("AI Picks — Running") so a voice assistant can be asked for it. {@code playlistId} is the copy there,
 * once written.
 */
public record PlaylistCopy(PersonId person, Optional<CategoryId> category, Provider service,
                           Optional<String> playlistId) {

    public PlaylistCopy {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(playlistId, "playlistId");
    }

    public static PlaylistCopy wanted(PersonId person, Optional<CategoryId> category, Provider service) {
        return new PlaylistCopy(person, category, service, Optional.empty());
    }

    public PlaylistCopy writtenAs(String id) {
        return new PlaylistCopy(person, category, service, Optional.of(id));
    }
}
