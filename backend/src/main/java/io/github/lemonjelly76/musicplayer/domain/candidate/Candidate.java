package io.github.lemonjelly76.musicplayer.domain.candidate;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * An album in the pool, its score, and the signals behind the score so the model can weigh them itself; cautions say
 * what counted against it.
 */
public record Candidate(AlbumRef album, double score, SortedSet<String> sources, List<String> seeds, int seedPlays,
                        List<String> cautions) {

    public Candidate {
        Objects.requireNonNull(album, "album");
        sources = Collections.unmodifiableSortedSet(new TreeSet<>(sources));
        seeds = List.copyOf(seeds);
        cautions = List.copyOf(cautions);
    }

    public Candidate withAlbum(AlbumRef resolved) {
        return new Candidate(resolved, score, sources, seeds, seedPlays, cautions);
    }
}
