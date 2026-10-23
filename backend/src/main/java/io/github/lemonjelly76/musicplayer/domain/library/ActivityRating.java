package io.github.lemonjelly76.musicplayer.domain.library;

import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A person's rating of an album or song for one activity; no category means Mainstream, an activity like the others.
 */
public record ActivityRating(Optional<CategoryId> category, Rating rating, String comment, Instant ratedAt) {

    public ActivityRating {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(rating, "rating");
        comment = Objects.requireNonNull(comment, "comment").strip();
        Objects.requireNonNull(ratedAt, "ratedAt");
    }

    public static ActivityRating mainstream(Rating rating, Instant ratedAt) {
        return new ActivityRating(Optional.empty(), rating, "", ratedAt);
    }
}
