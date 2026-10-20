package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.candidate.Candidate;
import io.github.lemonjelly76.musicplayer.domain.candidate.CandidatePool;
import io.github.lemonjelly76.musicplayer.domain.candidate.Seed;
import io.github.lemonjelly76.musicplayer.domain.candidate.Suggestion;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryFilter;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.port.SimilaritySource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Verdict;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /**
     * Stands in for the album when only an artist is given; sources that work by artist ignore it.
     */
    public static final String ANY_ALBUM = "(any album)";

    static final int MAX_SEEDS = 8;
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
    private final RecommendationService recommendations;
    private final List<SimilaritySource> sources;
    private final MusicProviders providers;
    private final Clock clock;

    public CandidateService(LibraryService library, PlayService plays, RecommendationService recommendations,
            List<SimilaritySource> sources, MusicProviders providers, Clock clock) {
        this.library = library;
        this.plays = plays;
        this.recommendations = recommendations;
        this.sources = List.copyOf(sources);
        this.providers = providers;
        this.clock = clock;
    }

    public List<Candidate> candidates(Person person, Optional<Category> category, int limit) {
        List<Seed> seeds = seeds(person, category);
        Set<AlbumKey> excluded = new HashSet<>(library.keys(person.id()));
        excluded.addAll(alreadyOffered(person, category));
        return playable(person, CandidatePool.rank(suggestions(person, seeds), excluded), limit);
    }

    /**
     * Candidates from one album alone, for the app's "More like this": instant, and no model involved.
     */
    public List<Candidate> moreLikeThis(Person person, AlbumRef album, int limit) {
        Seed seed = library.find(person.id(), album.key())
                .map(entry -> new Seed(entry.album(), entry.rating(), 0))
                .orElseGet(() -> Seed.of(album));
        Set<AlbumKey> excluded = new HashSet<>(library.keys(person.id()));
        excluded.addAll(alreadyOffered(person, Optional.empty()));
        excluded.add(album.key());
        return playable(person, CandidatePool.rank(suggestions(person, List.of(seed)), excluded), limit);
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
     * The person's best-rated albums and, when counting is on, their most played, best first.
     */
    List<Seed> seeds(Person person, Optional<Category> category) {
        Map<AlbumKey, Integer> playCounts = plays.playsByAlbum(person, category, PLAY_WINDOW_DAYS);
        Map<AlbumKey, Seed> seeds = new LinkedHashMap<>();
        for (LibraryAlbum entry : library.list(person.id(), LibraryFilter.ALL)) {
            AlbumKey key = entry.album().key();
            seeds.put(key, new Seed(entry.album(), entry.rating(), playCounts.getOrDefault(key, 0)));
        }
        return seeds.values().stream()
                .sorted(Comparator.comparingDouble(CandidateService::seedWeight).reversed())
                .limit(MAX_SEEDS)
                .toList();
    }

    private static double seedWeight(Seed seed) {
        return seed.rating().map(rating -> rating.value()).orElse(3) + Math.log1p(seed.plays());
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

    /**
     * Albums recommended in the last four weeks, ones the person already knows, and ones they turned down for this
     * category (an album rejected for Running can still come up under Driving).
     */
    private Set<AlbumKey> alreadyOffered(Person person, Optional<Category> category) {
        Instant recent = clock.instant().minus(RECENTLY_RECOMMENDED);
        Set<AlbumKey> offered = new HashSet<>();
        for (RecommendationList list : recommendations.history(person, HISTORY_LISTS)) {
            boolean sameCategory = list.category().equals(category.map(Category::id));
            for (Recommendation pick : list.picks()) {
                if (list.createdAt().isAfter(recent) || pick.has(Verdict.ALREADY_KNOW)
                        || (sameCategory && pick.has(Verdict.NOT_FOR_ME))) {
                    offered.add(pick.album().key());
                }
            }
        }
        return offered;
    }

    private List<Candidate> playable(Person person, List<Candidate> ranked, int limit) {
        Optional<MusicProvider> service = providers.forPerson(person);
        if (service.isEmpty()) {
            return ranked.stream().limit(limit).toList();
        }
        MusicProvider provider = service.get();
        List<Candidate> kept = new ArrayList<>();
        int lookups = 0;
        for (Candidate candidate : ranked) {
            if (kept.size() == limit || lookups == limit * LOOKUPS_PER_WANTED) {
                break;
            }
            AlbumRef album = candidate.album();
            if (album.ids().get(provider.name()).isPresent()) {
                kept.add(candidate);
                continue;
            }
            lookups++;
            provider.findAlbum(album.artist(), album.title())
                    .ifPresent(found -> kept.add(candidate.withAlbum(album.enrichedWith(found))));
        }
        return kept;
    }
}
