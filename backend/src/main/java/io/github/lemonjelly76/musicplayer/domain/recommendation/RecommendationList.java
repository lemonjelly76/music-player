package io.github.lemonjelly76.musicplayer.domain.recommendation;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A named batch of picks for a person, for a category or for everything, and the playlist it was synced to.
 */
public record RecommendationList(RecommendationListId id, PersonId person, Optional<CategoryId> category, String name,
                                 Instant createdAt, Optional<SyncedPlaylist> playlist, List<Recommendation> picks) {

    public static final String BASE_NAME = "AI Picks";

    public RecommendationList {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(playlist, "playlist");
        picks = List.copyOf(picks);
    }

    public static RecommendationList create(PersonId person, Optional<Category> category, List<Recommendation> picks,
            Instant createdAt) {
        return new RecommendationList(RecommendationListId.random(), person, category.map(Category::id),
                nameFor(category), createdAt, Optional.empty(), picks);
    }

    /**
     * Predictable names, so "Hey Google, play AI Picks — Running" finds the list on the streaming service.
     */
    public static String nameFor(Optional<Category> category) {
        return category.map(found -> BASE_NAME + " — " + found.name()).orElse(BASE_NAME);
    }

    public RecommendationList syncedTo(SyncedPlaylist synced) {
        return new RecommendationList(id, person, category, name, createdAt, Optional.of(synced), picks);
    }

    public Optional<Recommendation> pick(RecommendationId pickId) {
        return picks.stream().filter(pick -> pick.id().equals(pickId)).findFirst();
    }

    public record SyncedPlaylist(Provider provider, String playlistId) {

        public SyncedPlaylist {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(playlistId, "playlistId");
        }
    }
}
