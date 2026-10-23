package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.candidate.Candidate;
import io.github.lemonjelly76.musicplayer.domain.candidate.CandidatePool;
import io.github.lemonjelly76.musicplayer.domain.candidate.Seed;
import io.github.lemonjelly76.musicplayer.domain.candidate.Suggestion;
import io.github.lemonjelly76.musicplayer.domain.candidate.TurnedDown;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.port.SimilaritySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Steps 1 to 3 of the recommendation pipeline, done by plain code: gather albums similar to the person's best-loved
 * ones, drop what they should not be offered, rank the rest, and keep only what their service can play. The model
 * chooses from the result; the app's "More like this" shows it as it is.
 */
public class CandidateService {

    private static final System.Logger LOG = System.getLogger(CandidateService.class.getName());

    /**
     * Stands in for the album when only an artist is given; sources that work by artist ignore it.
     */
    public static final String ANY_ALBUM = "(any album)";

    static final int MAX_SEEDS = 8;
    /**
     * Seeds of each other kind at most: loved artists, what is on repeat and the albums of loved songs.
     */
    static final int OTHER_SEEDS = 2;
    /**
     * Seeds kept for the other kinds, however many albums are loved.
     */
    static final int RESERVED_SEEDS = 3;
    /**
     * The most recent albums turned down for the activity whose similar albums count against the pool.
     */
    static final int TURNED_DOWN_SEEDS = 4;
    static final int SUGGESTIONS_PER_SEED = 10;
    static final int PLAY_WINDOW_DAYS = 90;
    static final Duration RECENTLY_RECOMMENDED = Duration.ofDays(28);
    static final int HISTORY_LISTS = 50;
    /**
     * At most this many availability checks per album wanted, so a pool of strangers cannot run up hundreds of
     * catalogue calls.
     */
    static final int LOOKUPS_PER_WANTED = 3;

    private final LibraryService library;
    private final PlayService plays;
    private final SongService songs;
    private final ArtistService artists;
    private final RecommendationService recommendations;
    private final List<SimilaritySource> sources;
    private final MusicProviders providers;
    private final Clock clock;

    public CandidateService(LibraryService library, PlayService plays, SongService songs, ArtistService artists,
            RecommendationService recommendations, List<SimilaritySource> sources, MusicProviders providers,
            Clock clock) {
        this.library = library;
        this.plays = plays;
        this.songs = songs;
        this.artists = artists;
        this.recommendations = recommendations;
        this.sources = List.copyOf(sources);
        this.providers = providers;
        this.clock = clock;
    }

    public List<Candidate> candidates(Person person, Optional<Category> category, int limit) {
        List<Seed> seeds = seeds(person, category);
        Optional<CategoryId> activity = category.map(Category::id);
        List<Seed> turnedDownSeeds = library.dislikedFor(person.id(), activity).stream()
                .sorted(Comparator.comparing((LibraryAlbum entry) -> entry.own(activity).orElseThrow().ratedAt())
                        .reversed())
                .limit(TURNED_DOWN_SEEDS)
                .map(entry -> Seed.of(entry.album()))
                .toList();
        TurnedDown turnedDown = new TurnedDown(artists.turnedDown(person.id(), activity).keySet(),
                albumArtistsTurnedDown(person, activity), suggestions(person, turnedDownSeeds));
        return playable(person, CandidatePool.rank(suggestions(person, seeds), excluded(person, category), turnedDown),
                limit);
    }

    /**
     * Candidates from one album alone, for the app's "More like this": instant, and no model involved.
     */
    public List<Candidate> moreLikeThis(Person person, AlbumRef album, Optional<Category> category, int limit) {
        Set<AlbumKey> excluded = excluded(person, category);
        excluded.add(album.key());
        Optional<CategoryId> activity = category.map(Category::id);
        TurnedDown turnedDown = new TurnedDown(artists.turnedDown(person.id(), activity).keySet(),
                albumArtistsTurnedDown(person, activity), List.of());
        return playable(person, CandidatePool.rank(suggestions(person, List.of(Seed.of(album))), excluded, turnedDown),
                limit);
    }

    /**
     * What every source suggests for one album or artist, unfiltered, for the model to explore with.
     */
    public List<Candidate> similar(AlbumRef album, int limit) {
        Seed seed = Seed.of(album);
        List<Suggestion> suggestions = new ArrayList<>();
        for (SimilaritySource source : sources) {
            source.similarTo(album, SUGGESTIONS_PER_SEED).forEach(found -> suggestions.add(new Suggestion(found, source.name(), seed)));
        }
        return CandidatePool.rank(suggestions, Set.of(album.key())).stream().limit(limit).toList();
    }

    /**
     * What the search starts from, all of it the activity's own, as each activity's taste is: the albums loved
     * there, most played first when counting is on, then up to {@value #OTHER_SEEDS} each of the artists loved there,
     * albums on repeat there that are not rated there and albums of songs loved there. One with nothing of the kind
     * has no seeds, and its picks come from its rules alone.
     */
    List<Seed> seeds(Person person, Optional<Category> category) {
        Optional<CategoryId> activity = category.map(Category::id);
        List<LibraryAlbum> entries = library.entries(person.id());
        Set<AlbumKey> rated = new HashSet<>();
        entries.stream().filter(entry -> entry.own(activity).isPresent()).forEach(entry -> rated.add(entry.album().key()));
        Map<AlbumKey, Integer> playCounts = plays.playsByAlbum(person, category, PLAY_WINDOW_DAYS);
        List<Seed> loved = entries.stream()
                .filter(entry -> lovedFor(entry, activity))
                .sorted(Comparator.comparing((LibraryAlbum entry) -> playCounts.getOrDefault(entry.album().key(), 0))
                        .reversed()
                        .thenComparing(entry -> entry.own(activity).orElseThrow().ratedAt(), Comparator.reverseOrder()))
                .map(entry -> new Seed(entry.album(), playCounts.getOrDefault(entry.album().key(), 0)))
                .toList();
        Set<AlbumKey> taken = new HashSet<>(rated);
        List<Seed> others = new ArrayList<>();
        artists.lovedFor(person.id(), activity).stream()
                .limit(OTHER_SEEDS)
                .forEach(artist -> others.add(new Seed(AlbumRef.of(artist.name(), ANY_ALBUM), 0,
                        Seed.Origin.LOVED_ARTIST)));
        plays.stats(person, category, PLAY_WINDOW_DAYS).orElse(List.of()).stream()
                .sorted(Comparator.comparingInt(AlbumPlays::plays).reversed())
                .filter(total -> taken.add(total.album().key()))
                .limit(OTHER_SEEDS)
                .forEach(total -> others.add(new Seed(total.album(), total.plays(), Seed.Origin.ON_REPEAT)));
        songs.songsFor(person.id(), activity).stream()
                .filter(song -> song.own(activity).map(found -> found.rating() == Rating.LOVE).orElse(false))
                .filter(song -> !song.track().album().isBlank())
                .map(song -> AlbumRef.of(song.track().artist(), song.track().album()))
                .filter(album -> taken.add(album.key()))
                .limit(OTHER_SEEDS)
                .forEach(album -> others.add(new Seed(album, playCounts.getOrDefault(album.key(), 0),
                        Seed.Origin.LOVED_SONG)));
        int otherSeeds = Math.min(others.size(), Math.max(RESERVED_SEEDS, MAX_SEEDS - loved.size()));
        List<Seed> seeds = new ArrayList<>(loved.subList(0, Math.min(loved.size(), MAX_SEEDS - otherSeeds)));
        seeds.addAll(others.subList(0, otherSeeds));
        return seeds;
    }

    private Set<String> albumArtistsTurnedDown(Person person, Optional<CategoryId> activity) {
        Set<String> artists = new HashSet<>();
        library.dislikedFor(person.id(), activity).forEach(entry -> artists.add(entry.album().artist()));
        return artists;
    }

    private static boolean lovedFor(LibraryAlbum entry, Optional<CategoryId> activity) {
        return entry.own(activity).map(found -> found.rating() == Rating.LOVE).orElse(false);
    }

    /**
     * Albums already rated for the activity or kept for any, and anything picked in the last four weeks.
     */
    private Set<AlbumKey> excluded(Person person, Optional<Category> category) {
        Optional<CategoryId> activity = category.map(Category::id);
        Set<AlbumKey> excluded = new HashSet<>();
        library.entries(person.id()).stream()
                .filter(entry -> entry.knownFor(activity))
                .forEach(entry -> excluded.add(entry.album().key()));
        Instant recent = clock.instant().minus(RECENTLY_RECOMMENDED);
        recommendations.history(person, HISTORY_LISTS).stream()
                .filter(list -> list.createdAt().isAfter(recent))
                .flatMap(list -> list.picks().stream())
                .forEach(pick -> excluded.add(pick.album().key()));
        return excluded;
    }

    private List<Suggestion> suggestions(Person person, List<Seed> seeds) {
        Optional<MusicProvider> service = providers.forPerson(person);
        List<Suggestion> suggestions = new ArrayList<>();
        for (Seed seed : seeds) {
            for (SimilaritySource source : sources) {
                source.similarTo(seed.album(), SUGGESTIONS_PER_SEED)
                        .forEach(album -> suggestions.add(new Suggestion(album, source.name(), seed)));
            }
            service.ifPresent(provider -> seed.album().ids().get(provider.name()).ifPresent(id ->
                    similarOnService(provider, id).forEach(album ->
                            suggestions.add(new Suggestion(album, provider.name().prefix(), seed)))));
        }
        return suggestions;
    }

    /**
     * The service's own similar albums are one source among several, so the pool goes on without them.
     */
    private static List<AlbumRef> similarOnService(MusicProvider provider, String id) {
        try {
            return provider.similarAlbums(id, SUGGESTIONS_PER_SEED);
        } catch (MusicProviderUnavailableException e) {
            return List.of();
        }
    }

    private List<Candidate> playable(Person person, List<Candidate> ranked, int limit) {
        Optional<MusicProvider> service = providers.forPerson(person);
        if (service.isEmpty()) {
            return ranked.stream().limit(limit).toList();
        }
        MusicProvider provider = service.get();
        List<Candidate> kept = new ArrayList<>();
        Set<String> keptIds = new HashSet<>();
        int lookups = 0;
        for (Candidate candidate : ranked) {
            if (kept.size() == limit || lookups == limit * LOOKUPS_PER_WANTED) {
                break;
            }
            AlbumRef album = candidate.album();
            if (album.ids().get(provider.name()).isPresent()) {
                keepOnce(candidate, provider, kept, keptIds);
                continue;
            }
            lookups++;
            try {
                provider.findAlbum(album.artist(), album.title())
                        .ifPresent(found -> keepOnce(candidate.withAlbum(album.enrichedWith(found)), provider, kept,
                                keptIds));
            } catch (MusicProviderUnavailableException e) {
                if (kept.isEmpty()) {
                    throw e;
                }
                LOG.log(System.Logger.Level.WARNING, "Kept " + kept.size() + " checked albums; "
                        + provider.name().prefix() + " stopped answering", e);
                break;
            }
        }
        return kept;
    }

    /**
     * Two titles from different sources can turn out to be one album on the service; it is offered once.
     */
    private static void keepOnce(Candidate candidate, MusicProvider provider, List<Candidate> kept, Set<String> ids) {
        if (ids.add(candidate.album().ids().get(provider.name()).orElseThrow())) {
            kept.add(candidate);
        }
    }
}
