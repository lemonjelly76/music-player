package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartPlaylist;
import io.github.lemonjelly76.musicplayer.domain.chart.ResolvedChart;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbumId;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySongId;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistCopy;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.playlist.TrackMatch;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayBatch;
import io.github.lemonjelly76.musicplayer.domain.port.AlbumCatalogue;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.ChartRepository;
import io.github.lemonjelly76.musicplayer.domain.port.LibraryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.LookupCache;
import io.github.lemonjelly76.musicplayer.domain.port.ListeningHistory;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlayRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistCopyRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistRepository;
import io.github.lemonjelly76.musicplayer.domain.port.RecommendationRepository;
import io.github.lemonjelly76.musicplayer.domain.port.SimilaritySource;
import io.github.lemonjelly76.musicplayer.domain.port.SongRepository;
import io.github.lemonjelly76.musicplayer.domain.port.TasteProfileRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationListId;
import io.github.lemonjelly76.musicplayer.domain.taste.TasteProfile;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In-memory stand-ins for the ports, so application services are tested without a database or the network.
 */
final class FakePorts {

    private FakePorts() {
    }

    static final class People implements PersonRepository {

        final Map<PersonId, Person> saved = new LinkedHashMap<>();

        @Override
        public List<Person> findAll() {
            return List.copyOf(saved.values());
        }

        @Override
        public Optional<Person> findById(PersonId id) {
            return Optional.ofNullable(saved.get(id));
        }

        @Override
        public void save(Person person) {
            saved.put(person.id(), person);
        }
    }

    static final class Library implements LibraryRepository {

        final Map<LibraryAlbumId, LibraryAlbum> saved = new LinkedHashMap<>();

        @Override
        public List<LibraryAlbum> findByPerson(PersonId person) {
            return saved.values().stream().filter(entry -> entry.person().equals(person)).toList();
        }

        @Override
        public Optional<LibraryAlbum> findById(LibraryAlbumId id) {
            return Optional.ofNullable(saved.get(id));
        }

        @Override
        public void save(LibraryAlbum album) {
            saved.put(album.id(), album);
        }

        @Override
        public void delete(LibraryAlbumId id) {
            saved.remove(id);
        }
    }

    static final class Profiles implements TasteProfileRepository {

        final Map<List<Object>, TasteProfile> saved = new HashMap<>();

        @Override
        public Optional<TasteProfile> find(PersonId person, Optional<CategoryId> category) {
            return Optional.ofNullable(saved.get(List.of(person, category)));
        }

        @Override
        public void save(TasteProfile profile) {
            saved.put(List.of(profile.person(), profile.category()), profile);
        }
    }

    static final class Categories implements CategoryRepository {

        final Map<CategoryId, Category> saved = new LinkedHashMap<>();

        @Override
        public List<Category> findAll() {
            return List.copyOf(saved.values());
        }

        @Override
        public Optional<Category> findById(CategoryId id) {
            return Optional.ofNullable(saved.get(id));
        }

        @Override
        public void save(Category category) {
            saved.put(category.id(), category);
        }

        @Override
        public void reorder(List<CategoryId> order) {
            Map<CategoryId, Category> reordered = new LinkedHashMap<>();
            order.forEach(id -> reordered.put(id, saved.get(id)));
            saved.clear();
            saved.putAll(reordered);
        }

        @Override
        public void delete(CategoryId id) {
            saved.remove(id);
        }
    }

    static final class Plays implements PlayRepository {

        final Map<PersonId, List<CategorisedPlay>> saved = new HashMap<>();
        final Map<PersonId, Map<CategoryId, Boolean>> overrides = new HashMap<>();
        final Map<String, String> cursors = new HashMap<>();

        @Override
        public void add(PersonId person, Collection<CategorisedPlay> plays) {
            saved.computeIfAbsent(person, ignored -> new ArrayList<>()).addAll(plays);
        }

        @Override
        public List<AlbumPlays> albumTotals(PersonId person, Optional<CategoryId> category, Instant since) {
            Map<AlbumKey, List<CategorisedPlay>> byAlbum = saved.getOrDefault(person, List.of()).stream()
                    .filter(play -> category.isEmpty() || play.category().equals(category))
                    .filter(play -> !play.play().playedAt().isBefore(since))
                    .collect(Collectors.groupingBy(play -> play.play().album().key(), LinkedHashMap::new,
                            Collectors.toList()));
            return byAlbum.values().stream()
                    .map(plays -> new AlbumPlays(plays.getFirst().play().album(), plays.size(),
                            plays.stream().map(play -> play.play().playedAt()).max(Comparator.naturalOrder()).orElseThrow()))
                    .sorted(Comparator.comparingInt(AlbumPlays::plays).reversed())
                    .toList();
        }

        @Override
        public void clear(PersonId person, Optional<CategoryId> category) {
            saved.getOrDefault(person, new ArrayList<>())
                    .removeIf(play -> category.isEmpty() || play.category().equals(category));
        }

        @Override
        public Map<CategoryId, Boolean> trackingOverrides(PersonId person) {
            return overrides.getOrDefault(person, Map.of());
        }

        @Override
        public void setTrackingOverride(PersonId person, CategoryId category, Optional<Boolean> enabled) {
            Map<CategoryId, Boolean> forPerson = overrides.computeIfAbsent(person, ignored -> new HashMap<>());
            enabled.ifPresentOrElse(value -> forPerson.put(category, value), () -> forPerson.remove(category));
        }

        @Override
        public Optional<String> cursor(PersonId person, String source) {
            return Optional.ofNullable(cursors.get(person + source));
        }

        @Override
        public void saveCursor(PersonId person, String source, String cursor) {
            cursors.put(person + source, cursor);
        }
    }

    static final class Recommendations implements RecommendationRepository {

        final Map<RecommendationListId, RecommendationList> saved = new LinkedHashMap<>();

        @Override
        public void save(RecommendationList list) {
            saved.put(list.id(), list);
        }

        @Override
        public Optional<RecommendationList> findById(RecommendationListId id) {
            return Optional.ofNullable(saved.get(id));
        }

        @Override
        public void delete(RecommendationListId id) {
            saved.remove(id);
        }

        @Override
        public List<RecommendationList> findByPerson(PersonId person, Optional<CategoryId> category, int limit) {
            return saved.values().stream()
                    .filter(list -> list.person().equals(person))
                    .filter(list -> category.isEmpty() || list.category().equals(category))
                    .sorted(Comparator.comparing(RecommendationList::createdAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<RecommendationList> findByPlaylist(String playlistId) {
            return saved.values().stream()
                    .filter(list -> list.playlist().map(synced -> synced.playlistId().equals(playlistId)).orElse(false))
                    .findFirst();
        }
    }

    static final class Charts implements ChartRepository {

        final Map<Provider, ResolvedChart> charts = new HashMap<>();
        final Map<PersonId, ChartPlaylist> playlists = new HashMap<>();

        @Override
        public void save(ResolvedChart chart) {
            charts.put(chart.provider(), chart);
        }

        @Override
        public Optional<ResolvedChart> find(Provider provider) {
            return Optional.ofNullable(charts.get(provider));
        }

        @Override
        public ChartPlaylist playlistFor(PersonId person) {
            return playlists.getOrDefault(person, ChartPlaylist.wantedBy(person));
        }

        @Override
        public void save(ChartPlaylist playlist) {
            playlists.put(playlist.person(), playlist);
        }
    }

    static final class Lookups implements LookupCache {

        final Map<String, Lookup<Track>> tracks = new HashMap<>();
        final Map<String, Lookup<AlbumRef>> albums = new HashMap<>();

        @Override
        public Optional<Lookup<Track>> track(Provider provider, String key) {
            return Optional.ofNullable(tracks.get(provider + key));
        }

        @Override
        public void saveTrack(Provider provider, String key, Lookup<Track> lookup) {
            tracks.put(provider + key, lookup);
        }

        @Override
        public Optional<Lookup<AlbumRef>> album(Provider provider, String key) {
            return Optional.ofNullable(albums.get(provider + key));
        }

        @Override
        public void saveAlbum(Provider provider, String key, Lookup<AlbumRef> lookup) {
            albums.put(provider + key, lookup);
        }
    }

    static final class Songs implements SongRepository {

        final Map<LibrarySongId, LibrarySong> saved = new LinkedHashMap<>();

        @Override
        public List<LibrarySong> findByPerson(PersonId person) {
            return saved.values().stream().filter(song -> song.person().equals(person)).toList();
        }

        @Override
        public void save(LibrarySong song) {
            saved.put(song.id(), song);
        }

        @Override
        public void delete(LibrarySongId id) {
            saved.remove(id);
        }
    }

    static final class Copies implements PlaylistCopyRepository {

        final List<PlaylistCopy> saved = new ArrayList<>();

        @Override
        public Optional<PlaylistCopy> find(PersonId person, Optional<CategoryId> activity) {
            return saved.stream().filter(copy -> copy.person().equals(person) && copy.category().equals(activity))
                    .findFirst();
        }

        @Override
        public List<PlaylistCopy> forPerson(PersonId person) {
            return saved.stream().filter(copy -> copy.person().equals(person)).toList();
        }

        @Override
        public List<PlaylistCopy> forActivity(CategoryId activity) {
            return saved.stream().filter(copy -> copy.category().equals(Optional.of(activity))).toList();
        }

        @Override
        public Optional<PlaylistCopy> findByPlaylist(String providerPlaylistId) {
            return saved.stream().filter(copy -> copy.playlistId().equals(Optional.of(providerPlaylistId))).findFirst();
        }

        @Override
        public void save(PlaylistCopy copy) {
            delete(copy.person(), copy.category());
            saved.add(copy);
        }

        @Override
        public void delete(PersonId person, Optional<CategoryId> activity) {
            saved.removeIf(copy -> copy.person().equals(person) && copy.category().equals(activity));
        }
    }

    static final class Playlists implements PlaylistRepository {

        final Map<String, Playlist> saved = new LinkedHashMap<>();

        /**
         * Newest first; the test clock stands still, so a later save counts as newer.
         */
        private Stream<Playlist> newestFirst() {
            List<Playlist> all = new ArrayList<>(saved.values());
            Collections.reverse(all);
            return all.stream().sorted(Comparator.comparing(Playlist::createdAt).reversed());
        }

        @Override
        public void save(Playlist playlist) {
            saved.put(playlist.id().toString(), playlist);
        }

        @Override
        public List<Playlist> latest(PersonId person, Optional<CategoryId> activity, int limit) {
            return newestFirst()
                    .filter(playlist -> playlist.person().equals(person) && playlist.category().equals(activity))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<Playlist> find(PlaylistId id) {
            return Optional.ofNullable(saved.get(id.toString()));
        }

        @Override
        public void delete(PlaylistId id) {
            saved.remove(id.toString());
        }

        @Override
        public List<Playlist> inAccount(PersonId person, Optional<CategoryId> activity) {
            return latest(person, activity, Integer.MAX_VALUE).stream()
                    .filter(playlist -> playlist.slot().isPresent())
                    .toList();
        }

        @Override
        public List<Playlist> forActivity(CategoryId activity) {
            return newestFirst()
                    .filter(playlist -> playlist.category().equals(Optional.of(activity)))
                    .toList();
        }

        @Override
        public Optional<Playlist> findBySynced(String providerPlaylistId) {
            return saved.values().stream()
                    .filter(playlist -> playlist.synced()
                            .map(synced -> synced.playlistId().equals(providerPlaylistId)).orElse(false))
                    .findFirst();
        }
    }

    /**
     * A streaming service holding a fixed catalogue; albums it carries get its id.
     */
    static final class FakeProvider implements MusicProvider {

        final Provider name;
        final Map<String, AlbumRef> catalogue = new LinkedHashMap<>();
        final List<Track> songs = new ArrayList<>();
        final Map<String, List<AlbumRef>> similar = new HashMap<>();
        final Set<PersonId> connectedPeople = new HashSet<>();
        final Map<PersonId, List<String>> favourites = new HashMap<>();
        final Map<PersonId, List<AlbumRef>> storedFavourites = new HashMap<>();
        final List<String> playlistCalls = new ArrayList<>();
        final List<String> deletedPlaylists = new ArrayList<>();
        final List<String> renamedPlaylists = new ArrayList<>();
        int findCalls;
        int trackFinds;
        /** Searches after this many fail, as when the service goes down part way through. */
        int findsBeforeFailing = Integer.MAX_VALUE;
        boolean failing;
        private int playlistNumber;

        FakeProvider(Provider name) {
            this.name = name;
        }

        AlbumRef carry(String id, AlbumRef album) {
            AlbumRef onService = album.withIds(album.ids().with(name, id));
            catalogue.put(id, onService);
            return onService;
        }

        Track carryTrack(String id, String artist, String title, String album) {
            Track track = new Track(name, id, artist, title, album, Optional.empty());
            songs.add(track);
            return track;
        }

        @Override
        public Provider name() {
            return name;
        }

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public boolean connected(PersonId person) {
            return connectedPeople.contains(person);
        }

        @Override
        public List<AlbumRef> searchAlbums(String query, int limit) {
            return catalogue.values().stream()
                    .filter(album -> album.label().toLowerCase().contains(query.toLowerCase()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<AlbumRef> album(String id) {
            return Optional.ofNullable(catalogue.get(id));
        }

        @Override
        public Optional<AlbumRef> findAlbum(String artist, String title) {
            if (findCalls++ >= findsBeforeFailing) {
                throw new MusicProviderUnavailableException(name.prefix() + " stopped answering");
            }
            AlbumKey key = AlbumKey.of(artist, title);
            return catalogue.values().stream().filter(album -> album.key().equals(key)).findFirst();
        }

        @Override
        public List<Track> searchTracks(String query, int limit) {
            return songs.stream().filter(song -> song.label().toLowerCase().contains(query.toLowerCase()))
                    .limit(limit).toList();
        }

        @Override
        public Optional<Track> findTrack(String artist, String title) {
            trackFinds++;
            if (failing) {
                throw new MusicProviderUnavailableException(name.prefix() + " stopped answering");
            }
            return TrackMatch.best(songs, artist, title);
        }

        @Override
        public List<AlbumRef> similarAlbums(String id, int limit) {
            return similar.getOrDefault(id, List.of());
        }

        @Override
        public List<AlbumRef> favouriteAlbums(PersonId person) {
            requireConnected(person);
            return storedFavourites.getOrDefault(person, List.of());
        }

        @Override
        public void addFavourite(PersonId person, String id) {
            requireConnected(person);
            favourites.computeIfAbsent(person, ignored -> new ArrayList<>()).add(id);
        }

        @Override
        public String replacePlaylist(PersonId person, Optional<String> previousPlaylistId, String name,
                String description, List<String> trackIds) {
            requireConnected(person);
            previousPlaylistId.ifPresent(deletedPlaylists::add);
            playlistCalls.add(name + " " + trackIds);
            return "playlist-" + ++playlistNumber;
        }

        @Override
        public URI albumLink(String id) {
            return URI.create("https://" + name.prefix() + ".example/album/" + id);
        }

        @Override
        public URI searchLink(String query) {
            return URI.create("https://" + name.prefix() + ".example/search/" + query.replace(' ', '+'));
        }

        @Override
        public void renamePlaylist(PersonId person, String playlistId, String newName) {
            requireConnected(person);
            renamedPlaylists.add(playlistId + " " + newName);
        }

        @Override
        public void deletePlaylist(PersonId person, String playlistId) {
            requireConnected(person);
            deletedPlaylists.add(playlistId);
        }

        @Override
        public URI trackLink(String id) {
            return URI.create("https://" + name.prefix() + ".example/track/" + id);
        }

        @Override
        public URI playlistLink(String playlistId) {
            return URI.create("https://" + name.prefix() + ".example/playlist/" + playlistId);
        }

        private void requireConnected(PersonId person) {
            if (failing || !connected(person)) {
                throw new MusicProviderUnavailableException(name.prefix() + " is not connected");
            }
        }
    }

    static final class Catalogue implements AlbumCatalogue {

        final Map<AlbumKey, AlbumRef> albums = new HashMap<>();

        AlbumRef know(AlbumRef album) {
            albums.put(album.key(), album);
            return album;
        }

        @Override
        public Optional<AlbumRef> findAlbum(String artist, String title) {
            return Optional.ofNullable(albums.get(AlbumKey.of(artist, title)));
        }

        @Override
        public Optional<AlbumRef> byMusicBrainzId(String mbid) {
            return albums.values().stream().filter(album -> album.ids().musicBrainz().equals(Optional.of(mbid)))
                    .findFirst();
        }
    }

    static final class Similarity implements SimilaritySource {

        final String name;
        final Map<AlbumKey, List<AlbumRef>> suggestions = new HashMap<>();

        Similarity(String name) {
            this.name = name;
        }

        void suggest(AlbumRef seed, AlbumRef... albums) {
            suggestions.put(seed.key(), List.of(albums));
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<AlbumRef> similarTo(AlbumRef seed, int limit) {
            return suggestions.getOrDefault(seed.key(), List.of()).stream().limit(limit).toList();
        }
    }

    static final class History implements ListeningHistory {

        final String source;
        final Function<Optional<String>, PlayBatch> reader;
        final List<Optional<String>> cursorsAskedFor = new ArrayList<>();

        History(String source, Function<Optional<String>, PlayBatch> reader) {
            this.source = source;
            this.reader = reader;
        }

        @Override
        public String source() {
            return source;
        }

        @Override
        public boolean covers(Person person) {
            return true;
        }

        @Override
        public PlayBatch playsSince(Person person, Optional<String> cursor) {
            cursorsAskedFor.add(cursor);
            return reader.apply(cursor);
        }
    }
}
