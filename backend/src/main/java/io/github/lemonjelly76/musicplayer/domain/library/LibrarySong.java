package io.github.lemonjelly76.musicplayer.domain.library;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A song a person has rated, with at most one rating per activity, as albums are (ADR-015).
 */
public record LibrarySong(LibrarySongId id, PersonId person, Track track, List<ActivityRating> ratings,
                          LocalDate addedOn) {

    public LibrarySong {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(track, "track");
        ratings = ActivityRatings.checked(ratings);
        Objects.requireNonNull(addedOn, "addedOn");
    }

    public static LibrarySong create(PersonId person, Track track, ActivityRating rating, LocalDate addedOn) {
        return new LibrarySong(LibrarySongId.random(), person, track, List.of(rating), addedOn);
    }

    /**
     * The rating given for exactly this activity, with no fallback.
     */
    public Optional<ActivityRating> own(Optional<CategoryId> category) {
        return ActivityRatings.own(ratings, category);
    }

    /**
     * Whether the person turned it down for the activity, so it is left out of what is made for it.
     */
    public boolean avoidedFor(Optional<CategoryId> category) {
        return ActivityRatings.avoidedFor(ratings, category);
    }

    /**
     * Whether it is in the person's library for the activity: rated love or OK for exactly that activity.
     */
    public boolean keptFor(Optional<CategoryId> category) {
        return ActivityRatings.keptFor(ratings, category);
    }

    public LibrarySong rated(ActivityRating rating) {
        return new LibrarySong(id, person, track, ActivityRatings.with(ratings, rating), addedOn);
    }

    public LibrarySong unrated(Optional<CategoryId> category) {
        return new LibrarySong(id, person, track, ActivityRatings.without(ratings, category), addedOn);
    }

    /**
     * The same song found again, perhaps on another service, keeping its ratings.
     */
    public LibrarySong withTrack(Track found) {
        return new LibrarySong(id, person, found, ratings, addedOn);
    }
}
