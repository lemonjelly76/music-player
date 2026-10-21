package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
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

/**
 * Turns the model's choice of songs into a playlist: finds each on the person's service, keeps the first
 * {@value Playlist#SIZE} once each, and writes them to their account in place of the activity's previous playlist.
 */
public class PlaylistService {

    private static final System.Logger LOG = System.getLogger(PlaylistService.class.getName());
    private static final int EARLIER_LISTS = 10;

    private final PlaylistRepository repository;
    private final RecommendationRepository albumLists;
    private final LibraryService library;
    private final SongService songs;
    private final MusicProviders providers;
    private final Clock clock;

    public PlaylistService(PlaylistRepository repository, RecommendationRepository albumLists, LibraryService library,
            SongService songs, MusicProviders providers, Clock clock) {
        this.repository = repository;
        this.albumLists = albumLists;
        this.library = library;
        this.songs = songs;
        this.providers = providers;
        this.clock = clock;
    }

    public Saved save(Person person, Optional<Category> category, String description, List<TrackRequest> requests) {
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("A playlist needs at least one song");
        }
        MusicProvider service = providers.forPerson(person).orElseThrow(() -> new MusicProviderUnavailableException(
                person.name() + " has no streaming service set up, so there is nowhere to find the songs"));
        Optional<CategoryId> activity = category.map(Category::id);
        Map<AlbumKey, String> turnedDown = library.dislikedFor(person.id(), activity).stream()
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
     * Gives the activity's playlists its new name, here and, for each person's newest, in their account. An account
     * that cannot be reached keeps the old name until their next playlist replaces it.
     */
    public void renameFor(Category category) {
        String name = RecommendationList.nameFor(Optional.of(category));
        Set<PersonId> renamedInAccount = new HashSet<>();
        for (Playlist playlist : repository.forActivity(category.id())) {
            repository.save(playlist.renamed(name));
            if (playlist.synced().isPresent() && renamedInAccount.add(playlist.person())) {
                renameInAccount(playlist.person(), playlist.synced().get(), name);
            }
        }
    }

    private void renameInAccount(PersonId person, SyncedPlaylist synced, String name) {
        try {
            providers.find(synced.provider()).ifPresent(service -> service.renamePlaylist(person, synced.playlistId(), name));
        } catch (MusicProviderUnavailableException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not rename playlist " + synced.playlistId() + " to " + name, e);
        }
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
     * Songs found on another service than the person's now (they switched) cannot go in its playlist.
     */
    private Saved sync(Person person, Playlist playlist, MusicProvider service, List<Dropped> dropped) {
        List<String> trackIds = playlist.tracks().stream()
                .filter(track -> track.provider() == service.name())
                .map(Track::id)
                .toList();
        if (trackIds.isEmpty()) {
            return new Saved(playlist, dropped, "not synced: none of the songs are on " + service.name().prefix());
        }
        String description = playlist.description().isEmpty()
                ? "Picked for " + person.name() + " on " + LocalDate.now(clock)
                : playlist.description();
        try {
            String playlistId = service.replacePlaylist(person.id(), previous(playlist), playlist.name(), description,
                    trackIds);
            Playlist synced = playlist.syncedTo(new SyncedPlaylist(service.name(), playlistId));
            repository.save(synced);
            return new Saved(synced, dropped, "synced to " + service.name().prefix() + " as " + playlist.name());
        } catch (MusicProviderUnavailableException e) {
            return new Saved(playlist, dropped, "saved but not synced: " + e.getMessage());
        }
    }

    /**
     * The activity's playlist in the person's account: the last one written from songs, else one written from an
     * album list before playlists held songs.
     */
    private Optional<String> previous(Playlist playlist) {
        return repository.latest(playlist.person(), playlist.category(), EARLIER_LISTS).stream()
                .filter(earlier -> !earlier.id().equals(playlist.id()))
                .flatMap(earlier -> earlier.synced().stream())
                .map(SyncedPlaylist::playlistId)
                .findFirst()
                .or(() -> albumLists.findByPerson(playlist.person(), playlist.category(), EARLIER_LISTS).stream()
                        .filter(list -> list.category().equals(playlist.category())
                                && list.name().equals(playlist.name()))
                        .flatMap(list -> list.playlist().stream())
                        .map(SyncedPlaylist::playlistId)
                        .findFirst());
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
