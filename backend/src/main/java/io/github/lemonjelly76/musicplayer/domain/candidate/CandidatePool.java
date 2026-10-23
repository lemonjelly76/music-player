package io.github.lemonjelly76.musicplayer.domain.candidate;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Names;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Steps 2 and 3 of the recommendation pipeline: merges what the similarity sources suggest, drops what the person
 * should not be offered and what is no new album (mixes, outtakes, live albums, best-ofs), and ranks the rest on
 * simple signals.
 * <p>
 * Score = 2 per source that suggests the album + 1 per seed that leads to it + half the log2 of those seeds' play
 * counts, less {@value #ARTIST_TURNED_DOWN} for an artist turned down for the activity and {@value #NEAR_TURNED_DOWN}
 * for an album the sources also suggest for one turned down there. Several sources agreeing counts most; several
 * seeds agreeing comes next.
 * <p>
 * The order then keeps the pool varied: at most {@value #PER_ARTIST} albums by one artist, and each further album
 * from the same seed counts {@value #PER_SEED_SPREAD} less, so one loved album cannot fill the pool.
 */
public final class CandidatePool {

    static final int PER_ARTIST = 2;
    static final double PER_SEED_SPREAD = 1.0;
    static final double ARTIST_TURNED_DOWN = 2.0;
    static final double NEAR_TURNED_DOWN = 1.5;

    private CandidatePool() {
    }

    public static List<Candidate> rank(Collection<Suggestion> suggestions, Set<AlbumKey> excluded) {
        return rank(suggestions, excluded, TurnedDown.NONE);
    }

    public static List<Candidate> rank(Collection<Suggestion> suggestions, Set<AlbumKey> excluded,
            TurnedDown turnedDown) {
        Map<AlbumKey, List<String>> near = new HashMap<>();
        turnedDown.near().forEach(suggestion -> near.computeIfAbsent(suggestion.album().key(),
                ignored -> new ArrayList<>()).add(suggestion.seed().album().label()));
        return varied(tally(suggestions, excluded).stream()
                .map(tally -> tally.toCandidate(turnedDown, near.getOrDefault(tally.key, List.of())))
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .toList());
    }

    /**
     * One tally per album, matched by name or by an id on any service, as when two titles of one album resolve to it.
     */
    private static Collection<Tally> tally(Collection<Suggestion> suggestions, Set<AlbumKey> excluded) {
        Map<AlbumKey, Tally> tallies = new LinkedHashMap<>();
        Map<String, AlbumKey> byId = new HashMap<>();
        for (Suggestion suggestion : suggestions) {
            AlbumRef album = suggestion.album();
            if (excluded.contains(album.key()) || Filler.is(album.title())) {
                continue;
            }
            AlbumKey key = ids(album.ids()).map(byId::get).filter(tallies::containsKey).findFirst()
                    .orElse(album.key());
            Tally tally = tallies.computeIfAbsent(key, found -> new Tally(found, album));
            tally.add(suggestion);
            ids(tally.album.ids()).forEach(id -> byId.putIfAbsent(id, key));
        }
        return tallies.values();
    }

    private static Stream<String> ids(ProviderIds ids) {
        return Stream.of(ids.tidal().map(id -> "tidal:" + id), ids.spotify().map(id -> "spotify:" + id),
                ids.musicBrainz().map(id -> "mbid:" + id)).flatMap(Optional::stream);
    }

    /**
     * Takes the best album still allowed, each time counting the seed's earlier albums against it.
     */
    private static List<Candidate> varied(List<Candidate> ranked) {
        List<Candidate> left = new ArrayList<>(ranked);
        List<Candidate> chosen = new ArrayList<>();
        Map<String, Integer> byArtist = new HashMap<>();
        Map<String, Integer> bySeed = new HashMap<>();
        while (!left.isEmpty()) {
            Candidate best = left.stream()
                    .max(Comparator.comparingDouble(candidate -> candidate.score()
                            - PER_SEED_SPREAD * bySeed.getOrDefault(firstSeed(candidate), 0)))
                    .orElseThrow();
            left.remove(best);
            String artist = Names.normalise(best.album().artist());
            if (byArtist.merge(artist, 1, Integer::sum) <= PER_ARTIST) {
                chosen.add(best);
                bySeed.merge(firstSeed(best), 1, Integer::sum);
            }
        }
        return chosen;
    }

    private static String firstSeed(Candidate candidate) {
        return candidate.seeds().isEmpty() ? "" : candidate.seeds().getFirst();
    }

    private static final class Tally {

        private final AlbumKey key;
        private AlbumRef album;
        private final TreeSet<String> sources = new TreeSet<>();
        private final Map<AlbumKey, Seed> seeds = new LinkedHashMap<>();

        Tally(AlbumKey key, AlbumRef album) {
            this.key = key;
            this.album = album;
        }

        void add(Suggestion suggestion) {
            album = album.enrichedWith(suggestion.album());
            sources.add(suggestion.source());
            seeds.putIfAbsent(suggestion.seed().album().key(), suggestion.seed());
        }

        Candidate toCandidate(TurnedDown turnedDown, List<String> nearTurnedDown) {
            int plays = seeds.values().stream().mapToInt(Seed::plays).sum();
            double score = 2.0 * sources.size() + seeds.size() + 0.5 * log2(1 + plays);
            List<String> cautions = new ArrayList<>();
            if (turnedDown.artist(album.artist())) {
                score -= ARTIST_TURNED_DOWN;
                cautions.add("by an artist turned down here");
            }
            Set<String> like = new LinkedHashSet<>(nearTurnedDown);
            if (!like.isEmpty()) {
                score -= NEAR_TURNED_DOWN;
                cautions.add("like " + String.join(", ", like) + ", turned down here");
            }
            List<String> seedLabels = seeds.values().stream().map(Seed::label).toList();
            return new Candidate(album, round(score), sources, seedLabels, plays, cautions);
        }

        private static double log2(int value) {
            return Math.log(value) / Math.log(2);
        }

        private static double round(double value) {
            return Math.round(value * 100) / 100.0;
        }
    }
}
