package io.github.lemonjelly76.musicplayer.domain.library;

import io.github.lemonjelly76.musicplayer.domain.album.Names;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An artist a person has rated, with at most one rating per activity, as albums and songs are (ADR-015). Loving one
 * leads to their albums and artists like them; turning one down keeps everything of theirs out of that activity.
 * Artists are matched by name, so a band credited jointly with another counts as neither.
 */
public record LibraryArtist(LibraryArtistId id, PersonId person, String name, List<ActivityRating> ratings,
                            LocalDate addedOn) {

    private static final Pattern CREDITS = Pattern.compile("\\s*(?:,|&|\\bfeat\\.?|\\bft\\.|\\bfeaturing)\\s*",
            Pattern.CASE_INSENSITIVE);

    public LibraryArtist {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(person, "person");
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("An artist needs a name");
        }
        ratings = ActivityRatings.checked(ratings);
        Objects.requireNonNull(addedOn, "addedOn");
    }

    public static LibraryArtist create(PersonId person, String name, ActivityRating rating, LocalDate addedOn) {
        return new LibraryArtist(LibraryArtistId.random(), person, name, List.of(rating), addedOn);
    }

    /**
     * "The Strokes" and "strokes" are one artist.
     */
    public boolean isCalled(String artist) {
        return key(name).equals(key(artist));
    }

    public static String key(String artist) {
        return Names.normalise(artist);
    }

    /**
     * Everyone credited, by key, so turning one down also keeps out what they made with others: "Sam Fender &
     * Olivia Dean" is both, as well as the pair. A band such as "Mumford & Sons" still matches by its whole name.
     */
    public static Set<String> credited(String credit) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(key(credit));
        for (String part : CREDITS.split(credit)) {
            String found = key(part);
            if (!found.isEmpty()) {
                keys.add(found);
            }
        }
        return keys;
    }

    public Optional<ActivityRating> own(Optional<CategoryId> category) {
        return ActivityRatings.own(ratings, category);
    }

    public boolean avoidedFor(Optional<CategoryId> category) {
        return ActivityRatings.avoidedFor(ratings, category);
    }

    public LibraryArtist rated(ActivityRating rating) {
        return new LibraryArtist(id, person, name, ActivityRatings.with(ratings, rating), addedOn);
    }

    public LibraryArtist unrated(Optional<CategoryId> category) {
        return new LibraryArtist(id, person, name, ActivityRatings.without(ratings, category), addedOn);
    }
}
