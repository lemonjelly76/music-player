package io.github.lemonjelly76.musicplayer.domain.library;

import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What albums and songs share about their ratings: at most one per activity, and each activity holding only what
 * was rated for it (ADR-015). General's rating is on hand as a hint, and its Not for me keeps an album or song away
 * from activities that have no rating of their own for it.
 */
final class ActivityRatings {

    private ActivityRatings() {
    }

    static List<ActivityRating> checked(List<ActivityRating> ratings) {
        List<ActivityRating> copy = List.copyOf(ratings);
        if (copy.stream().map(ActivityRating::category).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("at most one rating per activity");
        }
        return copy;
    }

    static Optional<ActivityRating> own(List<ActivityRating> ratings, Optional<CategoryId> category) {
        return ratings.stream().filter(rating -> rating.category().equals(category)).findFirst();
    }

    static Optional<ActivityRating> ownOrGeneral(List<ActivityRating> ratings, Optional<CategoryId> category) {
        return own(ratings, category).or(() -> own(ratings, Optional.empty()));
    }

    static boolean keptFor(List<ActivityRating> ratings, Optional<CategoryId> category) {
        return own(ratings, category).map(found -> found.rating().keeps()).orElse(false);
    }

    static boolean avoidedFor(List<ActivityRating> ratings, Optional<CategoryId> category) {
        return ownOrGeneral(ratings, category).map(found -> found.rating() == Rating.DISLIKE).orElse(false);
    }

    static List<ActivityRating> with(List<ActivityRating> ratings, ActivityRating rating) {
        List<ActivityRating> changed = new ArrayList<>(ratings);
        changed.removeIf(existing -> existing.category().equals(rating.category()));
        changed.add(rating);
        changed.sort(Comparator.comparing(ActivityRating::ratedAt));
        return changed;
    }

    static List<ActivityRating> without(List<ActivityRating> ratings, Optional<CategoryId> category) {
        List<ActivityRating> changed = new ArrayList<>(ratings);
        changed.removeIf(existing -> existing.category().equals(category));
        return changed;
    }
}
