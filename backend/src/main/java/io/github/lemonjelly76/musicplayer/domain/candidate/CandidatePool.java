package io.github.lemonjelly76.musicplayer.domain.candidate;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Steps 2 and 3 of the recommendation pipeline: merges what the similarity sources suggest, drops what the person
 * should not be offered, and ranks the rest on simple signals.
 * <p>
 * Score = 2 per source that suggests the album + 1 per loved album that leads to it + half the log2 of those albums'
 * play counts. Several sources agreeing counts most; several loved albums agreeing comes next.
 */
public final class CandidatePool {

    private CandidatePool() {
    }

    public static List<Candidate> rank(Collection<Suggestion> suggestions, Set<AlbumKey> excluded) {
        Map<AlbumKey, Tally> tallies = new LinkedHashMap<>();
        for (Suggestion suggestion : suggestions) {
            AlbumKey key = suggestion.album().key();
            if (!excluded.contains(key)) {
                tallies.computeIfAbsent(key, ignored -> new Tally(suggestion.album())).add(suggestion);
            }
        }
        return tallies.values().stream()
                .map(Tally::toCandidate)
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .toList();
    }

    private static final class Tally {

        private AlbumRef album;
        private final TreeSet<String> sources = new TreeSet<>();
        private final Map<AlbumKey, Seed> seeds = new LinkedHashMap<>();

        Tally(AlbumRef album) {
            this.album = album;
        }

        void add(Suggestion suggestion) {
            album = album.enrichedWith(suggestion.album());
            sources.add(suggestion.source());
            seeds.putIfAbsent(suggestion.seed().album().key(), suggestion.seed());
        }

        Candidate toCandidate() {
            int plays = seeds.values().stream().mapToInt(Seed::plays).sum();
            double score = 2.0 * sources.size() + seeds.size() + 0.5 * log2(1 + plays);
            List<String> seedLabels = seeds.values().stream().map(seed -> seed.album().label()).toList();
            return new Candidate(album, round(score), sources, seedLabels, plays);
        }

        private static double log2(int value) {
            return Math.log(value) / Math.log(2);
        }

        private static double round(double value) {
            return Math.round(value * 100) / 100.0;
        }
    }
}
