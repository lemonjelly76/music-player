package io.github.lemonjelly76.musicplayer.domain.library;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * An album a person likes. Tags are kept lower case and sorted so "Jazz" and "jazz" are one tag.
 */
public record LibraryAlbum(LibraryAlbumId id, PersonId person, AlbumRef album, Optional<Rating> rating, String notes,
                           SortedSet<String> tags, LocalDate addedOn) {

    public LibraryAlbum {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(rating, "rating");
        notes = Objects.requireNonNull(notes, "notes").strip();
        tags = cleanTags(Objects.requireNonNull(tags, "tags"));
        Objects.requireNonNull(addedOn, "addedOn");
    }

    public static LibraryAlbum create(PersonId person, AlbumRef album, Optional<Rating> rating, String notes,
            Collection<String> tags, LocalDate addedOn) {
        return new LibraryAlbum(LibraryAlbumId.random(), person, album, rating, notes, cleanTags(tags), addedOn);
    }

    public LibraryAlbum withRating(Optional<Rating> newRating) {
        return new LibraryAlbum(id, person, album, newRating, notes, tags, addedOn);
    }

    public LibraryAlbum withNotes(String newNotes) {
        return new LibraryAlbum(id, person, album, rating, newNotes, tags, addedOn);
    }

    public LibraryAlbum withTags(Collection<String> newTags) {
        return new LibraryAlbum(id, person, album, rating, notes, cleanTags(newTags), addedOn);
    }

    public LibraryAlbum withAlbum(AlbumRef newAlbum) {
        return new LibraryAlbum(id, person, newAlbum, rating, notes, tags, addedOn);
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
