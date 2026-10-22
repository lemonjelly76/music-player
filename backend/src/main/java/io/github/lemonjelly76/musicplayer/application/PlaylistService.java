package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist.Slot;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistRepository;
import io.github.lemonjelly76.musicplayer.domain.port.RecommendationRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Turns the model's choice of songs into a playlist: finds each on the person's service, keeps the first
 * {@value Playlist#SIZE} once each, and writes them to their account. Each activity keeps up to
 * {@value Playlist#SLOTS} playlists there, numbered, and the person chooses which.
 */
public class PlaylistService {

    private static final System.Logger LOG = System.getLogger(PlaylistService.class.getName());
    private static final int EARLIER_LISTS = 10;

    private final PlaylistRepository repository;
    private final RecommendationRepository albumLists;
    private final LibraryService library;
    private final SongService songs;
    private final MusicProviders providers;
    private final PlaylistCopies copies;
    private final Clock clock;

    public PlaylistService(PlaylistRepository repository, RecommendationRepository albumLists, LibraryService library,
            SongService songs, MusicProviders providers, PlaylistCopies copies, Clock clock) {
        this.repository = repository;
        this.albumLists = albumLists;
        this.library = library;
        this.songs = songs;
        this.providers = providers;
        this.copies = copies;
        this.clock = clock;
    }

    public Saved save(Person person, Optional<Category> category, String description, List<TrackRequest> requests) {
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("A playlist needs at least one song");
        }
        MusicProvider service = providers.forPerson(person).orElseThrow(() -> new MusicProviderUnavailableException(
                person.name() + " has no streaming service set up, so there is nowhere to find the songs"));
        Optional<CategoryId> activity = category.map(Category::id);
        Map<AlbumKey, String> turnedDown = library.avoidedFor(person.id(), activity).stream()
                .collect(Collectors.toMap(entry -> entry.album().key(), entry -> entry.album().title(),
                        (first, second) -> first));
        Set<String> songsTurnedDown = songs.turnedDown(person.id(), activity);
        List<Track> tracks = new ArrayList<>();
        Set<String> songs = new HashSet<>();
        Set<String> ids = new HashSet<>();
        List<Dropped> dropped = new ArrayList<>();
        for (TrackRequest request : requests) {
            if (tracks.size() == Playlist.SIZE) {
                dropped.add(new Dropped(request.label(), "not needed, the playlist is full"));
                continue;
            }
            Optional<Track> found = service.findTrack(request.artist(), request.title());
            Optional<String> why = found.isEmpty()
                    ? Optional.of("not on " + service.name().prefix())
                    : whyLeftOut(found.get(), songs, ids, songsTurnedDown, turnedDown::get);
            if (why.isPresent()) {
                dropped.add(new Dropped(request.label(), why.get()));
            } else {
                Track track = found.get();
                songs.add(track.songKey());
                ids.add(track.id());
                tracks.add(track);
            }
        }
        if (tracks.isEmpty()) {
            throw new IllegalArgumentException("No song could be kept: " + dropped);
        }
        Playlist playlist = Playlist.create(person.id(), category, description.strip(), tracks, clock.instant());
        repository.save(playlist);
        return sync(person, playlist, service, dropped);
    }

    /**
     * Writes the activity's latest playlist to the person's service again, for one saved while it was unavailable.
     */
    public Saved resync(Person person, Optional<Category> category) {
        Playlist latest = latest(person, category.map(Category::id), 1).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("No " + category.map(Category::name).orElse("General")
                        + " playlist for " + person.name() + " yet"));
        MusicProvider service = providers.forPerson(person).orElseThrow(() -> new MusicProviderUnavailableException(
                person.name() + " has no streaming service set up"));
        return sync(person, latest, service, List.of());
    }

    /**
     * The activity's playlists, newest first; no activity means General's.
     */
    public List<Playlist> latest(Person person, Optional<CategoryId> activity, int limit) {
        return repository.latest(person.id(), activity, limit);
    }

    /**
     * Puts one of the person's playlists in their account as the activity's playlist in that slot. One already in
     * the account is renamed, swapping numbers with any playlist in that slot; otherwise it is written in place of
     * the playlist there.
     */
    public Playlist putInSlot(Person person, PlaylistId id, int number) {
        if (number < 1 || number > Playlist.SLOTS) {
            throw new IllegalArgumentException("A playlist slot is 1 to " + Playlist.SLOTS + ", not " + number);
        }
        Playlist chosen = repository.find(id).filter(found -> found.person().equals(person.id()))
                .orElseThrow(() -> new NotFoundException("No playlist " + id + " for " + person.name()));
        MusicProvider service = providers.forPerson(person).orElseThrow(() -> new MusicProviderUnavailableException(
                person.name() + " has no streaming service set up"));
        boolean inAccount = chosen.inAccountOn(service.name());
        if (inAccount && chosen.slot().orElseThrow().number() == number) {
            return chosen;
        }
        Optional<Playlist> there = held(person.id(), service, chosen.category()).stream()
                .filter(held -> held.slot().orElseThrow().number() == number)
                .findFirst();
        Playlist placed = inAccount
                ? swap(person, chosen, number, there)
                : write(person, chosen, service, number, there.flatMap(Playlist::synced).map(SyncedPlaylist::playlistId));
        there.filter(held -> !inAccount).ifPresent(held -> repository.save(held.outOfAccount()));
        repository.save(placed);
        renameMisnamed(person.id(), chosen.category());
        return placed;
    }

    /**
     * Gives the activity's playlists its new name, here and in each person's account. An account that cannot be
     * reached keeps the old names until its playlists next change.
     */
    public void renameFor(Category category) {
        String name = RecommendationList.nameFor(Optional.of(category));
        Set<PersonId> people = new HashSet<>();
        for (Playlist playlist : repository.forActivity(category.id())) {
            repository.save(playlist.renamed(name));
            people.add(playlist.person());
        }
        people.forEach(person -> renameMisnamed(person, Optional.of(category.id())));
        copies.renamed(category);
    }

    private Playlist swap(Person person, Playlist chosen, int number, Optional<Playlist> there) {
        int from = chosen.slot().orElseThrow().number();
        there.ifPresent(held -> renameInAccount(person.id(), held, held.nameInSlot(from)));
        renameInAccount(person.id(), chosen, chosen.nameInSlot(number));
        there.ifPresent(held -> repository.save(held.movedTo(from)));
        return chosen.movedTo(number);
    }

    private Playlist write(Person person, Playlist playlist, MusicProvider service, int number,
            Optional<String> replacing) {
        List<String> trackIds = playableOn(service, playlist);
        if (trackIds.isEmpty()) {
            throw new IllegalArgumentException("None of its songs are on " + service.name().prefix());
        }
        String playlistId = service.replacePlaylist(person.id(), replacing, playlist.nameInSlot(number),
                description(person, playlist), trackIds);
        return playlist.writtenTo(number, new SyncedPlaylist(service.name(), playlistId));
    }

    /**
     * Renames playlists in the account whose names there are out of date: written before playlists had numbers, or
     * before their activity was renamed. A name is only cosmetic, so an account that cannot be reached is left.
     */
    private void renameMisnamed(PersonId person, Optional<CategoryId> activity) {
        for (Playlist held : repository.inAccount(person, activity)) {
            if (held.misnamedInAccount()) {
                int number = held.slot().orElseThrow().number();
                try {
                    renameInAccount(person, held, held.nameInSlot(number));
                    repository.save(held.movedTo(number));
                } catch (MusicProviderUnavailableException e) {
                    LOG.log(System.Logger.Level.WARNING, "Could not rename playlist " + held.id(), e);
                }
            }
        }
    }

    private void renameInAccount(PersonId person, Playlist playlist, String name) {
        SyncedPlaylist synced = playlist.synced().orElseThrow();
        providers.get(synced.provider()).renamePlaylist(person, synced.playlistId(), name);
    }

    private static Optional<String> whyLeftOut(Track track, Set<String> songs, Set<String> ids,
            Set<String> songsTurnedDown, Function<AlbumKey, String> turnedDown) {
        if (songs.contains(track.songKey()) || ids.contains(track.id())) {
            return Optional.of("already in this playlist");
        }
        if (songsTurnedDown.contains(track.songKey())) {
            return Optional.of("they turned this song down for this");
        }
        return Optional.ofNullable(turnedDown.apply(track.albumKey()))
                .map(album -> "from " + album + ", which they turned down for this");
    }

    /**
     * Writes the playlist to the person's account in the activity's first free slot, else in place of the oldest
     * there; one already in a slot is written again in its own.
     */
    private Saved sync(Person person, Playlist playlist, MusicProvider service, List<Dropped> dropped) {
        if (playableOn(service, playlist).isEmpty()) {
            return new Saved(playlist, dropped, "not synced: none of the songs are on " + service.name().prefix());
        }
        boolean inAccount = playlist.inAccountOn(service.name());
        List<Playlist> held = held(person.id(), service, playlist.category()).stream()
                .filter(other -> !other.id().equals(playlist.id()))
                .toList();
        Optional<Playlist> replaced = inAccount || held.size() < Playlist.SLOTS
                ? Optional.empty() : Optional.of(held.getLast());
        int number = (inAccount ? playlist.slot() : replaced.flatMap(Playlist::slot)).map(Slot::number)
                .orElseGet(() -> firstFree(held));
        Optional<String> replacing = inAccount ? playlist.synced().map(SyncedPlaylist::playlistId)
                : replaced.isPresent() ? replaced.flatMap(Playlist::synced).map(SyncedPlaylist::playlistId)
                : held.isEmpty() ? albumListPlaylist(playlist) : Optional.empty();
        try {
            Playlist written = write(person, playlist, service, number, replacing);
            replaced.ifPresent(old -> repository.save(old.outOfAccount()));
            repository.save(written);
            renameMisnamed(person.id(), playlist.category());
            String synced = "synced to " + service.name().prefix() + " as " + written.nameInSlot(number);
            return new Saved(written, dropped, copies.copy(person, written).map(copied -> synced + "; " + copied)
                    .orElse(synced));
        } catch (MusicProviderUnavailableException e) {
            return new Saved(playlist, dropped, "saved but not synced: " + e.getMessage());
        }
    }

    /**
     * The activity's playlists in the person's account on that service, newest first. Those on a service they used
     * before stay there, keeping their numbers in case they switch back.
     */
    private List<Playlist> held(PersonId person, MusicProvider service, Optional<CategoryId> activity) {
        return repository.inAccount(person, activity).stream()
                .filter(playlist -> playlist.inAccountOn(service.name()))
                .toList();
    }

    private static int firstFree(List<Playlist> held) {
        Set<Integer> taken = held.stream().map(playlist -> playlist.slot().orElseThrow().number())
                .collect(Collectors.toSet());
        return IntStream.rangeClosed(1, Playlist.SLOTS).filter(number -> !taken.contains(number)).findFirst()
                .orElseThrow();
    }

    /**
     * Songs found on another service than the person's now (they switched) cannot go in its playlist.
     */
    private static List<String> playableOn(MusicProvider service, Playlist playlist) {
        return playlist.tracks().stream()
                .filter(track -> track.provider() == service.name())
                .map(Track::id)
                .toList();
    }

    private String description(Person person, Playlist playlist) {
        return playlist.description().isEmpty()
                ? "Picked for " + person.name() + " on " + LocalDate.now(clock)
                : playlist.description();
    }

    /**
     * The activity's playlist written from a list of albums before playlists held songs, to replace with the first
     * playlist of songs.
     */
    private Optional<String> albumListPlaylist(Playlist playlist) {
        return albumLists.findByPerson(playlist.person(), playlist.category(), EARLIER_LISTS).stream()
                .filter(list -> list.category().equals(playlist.category()) && list.name().equals(playlist.name()))
                .flatMap(list -> list.playlist().stream())
                .map(SyncedPlaylist::playlistId)
                .findFirst();
    }

    public record TrackRequest(String artist, String title) {

        public TrackRequest {
            if (artist == null || artist.isBlank() || title == null || title.isBlank()) {
                throw new IllegalArgumentException("Each song needs an artist and a title");
            }
        }

        String label() {
            return artist.strip() + " — " + title.strip();
        }
    }

    public record Dropped(String track, String why) {

        @Override
        public String toString() {
            return track + " (" + why + ")";
        }
    }

    public record Saved(Playlist playlist, List<Dropped> dropped, String sync) {
    }
}
