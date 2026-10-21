package io.github.lemonjelly76.musicplayer.domain.playlist;

import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Songs picked for a person to play straight through, for an activity or for everything, and the playlist in their
 * streaming account that holds them. Unlike a list of albums to try, it is made of single tracks.
 */
public record Playlist(PlaylistId id, PersonId person, Optional<CategoryId> category, String name, String description,
                       Instant createdAt, Optional<SyncedPlaylist> synced, List<Track> tracks) {

    public static final int SIZE = 50;

    public Playlist {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(synced, "synced");
        tracks = List.copyOf(tracks);
        if (tracks.size() > SIZE) {
            throw new IllegalArgumentException("A playlist holds at most " + SIZE + " tracks, not " + tracks.size());
        }
    }

    /**
     * Named as album lists were, so the activity's playlist in the person's account keeps its name.
     */
    public static Playlist create(PersonId person, Optional<Category> category, String description, List<Track> tracks,
            Instant createdAt) {
        return new Playlist(PlaylistId.random(), person, category.map(Category::id),
                RecommendationList.nameFor(category), description, createdAt, Optional.empty(), tracks);
    }

    public Playlist syncedTo(SyncedPlaylist playlist) {
        return new Playlist(id, person, category, name, description, createdAt, Optional.of(playlist), tracks);
    }
}
