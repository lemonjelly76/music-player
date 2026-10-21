package io.github.lemonjelly76.musicplayer.domain.library;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * An album a person has rated, with at most one rating per activity (ADR-015). Tags are kept lower case and sorted
 * so "Jazz" and "jazz" are one tag.
 */
public record LibraryAlbum(LibraryAlbumId id, PersonId person, AlbumRef album, List<ActivityRating> ratings,
                           String notes, SortedSet<String> tags, LocalDate addedOn) {

    public LibraryAlbum {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(album, "album");
        ratings = List.copyOf(ratings);
        if (ratings.stream().map(ActivityRating::category).distinct().count() != ratings.size()) {
            throw new IllegalArgumentException("an album has at most one rating per activity");
        }
        notes = Objects.requireNonNull(notes, "notes").strip();
        tags = cleanTags(Objects.requireNonNull(tags, "tags"));
        Objects.requireNonNull(addedOn, "addedOn");
    }

    public static LibraryAlbum create(PersonId person, AlbumRef album, ActivityRating rating, String notes,
            Collection<String> tags, LocalDate addedOn) {
        return new LibraryAlbum(LibraryAlbumId.random(), person, album, List.of(rating), notes, cleanTags(tags),
                addedOn);
    }

    /**
     * The rating given for exactly this activity, with no fallback.
     */
    public Optional<ActivityRating> own(Optional<CategoryId> category) {
        return ratings.stream().filter(rating -> rating.category().equals(category)).findFirst();
    }

    /**
     * The activity's own rating, else the General one.
     */
    public Optional<ActivityRating> rating(Optional<CategoryId> category) {
        return own(category).or(() -> own(Optional.empty()));
    }

    /**
     * Whether it is in the person's library for the activity: rated love or OK there, or in General.
     */
    public boolean keptFor(Optional<CategoryId> category) {
        return rating(category).map(found -> found.rating().keeps()).orElse(false);
    }

    /**
     * Whether recommending it for the activity would be no news: it is rated for the activity (or in General), or
     * kept for any activity. An album disliked only for Dinner can still be suggested for Running.
     */
    public boolean knownFor(Optional<CategoryId> category) {
        return rating(category).isPresent() || ratings.stream().anyMatch(found -> found.rating().keeps());
    }

    public LibraryAlbum rated(ActivityRating rating) {
        List<ActivityRating> changed = new ArrayList<>(ratings);
        changed.removeIf(existing -> existing.category().equals(rating.category()));
        changed.add(rating);
        changed.sort(Comparator.comparing(ActivityRating::ratedAt));
        return new LibraryAlbum(id, person, album, changed, notes, tags, addedOn);
    }

    public LibraryAlbum unrated(Optional<CategoryId> category) {
        List<ActivityRating> changed = new ArrayList<>(ratings);
        changed.removeIf(existing -> existing.category().equals(category));
        return new LibraryAlbum(id, person, album, changed, notes, tags, addedOn);
    }

    public LibraryAlbum withNotes(String newNotes) {
        return new LibraryAlbum(id, person, album, ratings, newNotes, tags, addedOn);
    }

    public LibraryAlbum withTags(Collection<String> newTags) {
        return new LibraryAlbum(id, person, album, ratings, notes, cleanTags(newTags), addedOn);
    }

    public LibraryAlbum withAlbum(AlbumRef newAlbum) {
        return new LibraryAlbum(id, person, newAlbum, ratings, notes, tags, addedOn);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag.strip().toLowerCase(Locale.ROOT));
    }

    private static SortedSet<String> cleanTags(Collection<String> tags) {
        TreeSet<String> clean = new TreeSet<>();
        tags.stream()
                .map(tag -> tag.strip().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .forEach(clean::add);
        return Collections.unmodifiableSortedSet(clean);
    }
}
