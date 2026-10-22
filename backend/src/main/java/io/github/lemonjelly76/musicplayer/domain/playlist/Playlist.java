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
 * Songs picked for a person to play straight through, for an activity or for everything. Unlike a list of albums to
 * try, it is made of single tracks. {@code synced} is the playlist last written to their streaming account, kept so
 * plays from it still count for the activity; {@code slot} says whether it is in their account now, as one of the
 * activity's {@value #SLOTS}, named with its number.
 */
public record Playlist(PlaylistId id, PersonId person, Optional<CategoryId> category, String name, String description,
                       Instant createdAt, Optional<SyncedPlaylist> synced, Optional<Slot> slot, List<Track> tracks) {

    public static final int SIZE = 50;
    public static final int SLOTS = 3;

    public Playlist {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(synced, "synced");
        Objects.requireNonNull(slot, "slot");
        if (slot.isPresent() && synced.isEmpty()) {
            throw new IllegalArgumentException("A playlist in the account needs its id there");
        }
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
                RecommendationList.nameFor(category), description, createdAt, Optional.empty(), Optional.empty(),
                tracks);
    }

    /**
     * Its name in the account in that slot: "AI Picks — Running 2".
     */
    public String nameInSlot(int number) {
        return name + " " + number;
    }

    /**
     * Gives it a new name here; the account keeps the old one until it is renamed there too.
     */
    public Playlist renamed(String newName) {
        return new Playlist(id, person, category, newName, description, createdAt, synced, slot, tracks);
    }

    /**
     * Written to the account as that slot's playlist.
     */
    public Playlist writtenTo(int number, SyncedPlaylist playlist) {
        return new Playlist(id, person, category, name, description, createdAt, Optional.of(playlist),
                Optional.of(new Slot(number, nameInSlot(number))), tracks);
    }

    /**
     * Renamed in the account for the slot it now holds.
     */
    public Playlist movedTo(int number) {
        return new Playlist(id, person, category, name, description, createdAt, synced,
                Optional.of(new Slot(number, nameInSlot(number))), tracks);
    }

    /**
     * Taken out of the account, as another took its slot.
     */
    public Playlist outOfAccount() {
        return new Playlist(id, person, category, name, description, createdAt, synced, Optional.empty(), tracks);
    }

    /**
     * Whether its name in the account is out of date, as after its activity was renamed.
     */
    public boolean misnamedInAccount() {
        return slot.map(held -> !held.nameInAccount().equals(nameInSlot(held.number()))).orElse(false);
    }

    /**
     * One of the activity's places in the person's account, and what the playlist there is called.
     */
    public record Slot(int number, String nameInAccount) {

        public Slot {
            if (number < 1 || number > SLOTS) {
                throw new IllegalArgumentException("A playlist slot is 1 to " + SLOTS + ", not " + number);
            }
            Objects.requireNonNull(nameInAccount, "nameInAccount");
        }
    }
}
