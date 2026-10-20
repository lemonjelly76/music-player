package io.github.lemonjelly76.musicplayer.domain.candidate;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;

import java.util.Objects;
import java.util.Optional;

/**
 * An album the search for new music starts from: a person's well-rated or much-played album.
 */
public record Seed(AlbumRef album, Optional<Rating> rating, int plays) {

    /**
     * An unrated seed, such as an imported favourite, counts as a middling 3.
     */
    static final int UNRATED = 3;

    public Seed {
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(rating, "rating");
        if (plays < 0) {
            throw new IllegalArgumentException("plays cannot be negative");
        }
    }

    public static Seed of(AlbumRef album) {
        return new Seed(album, Optional.empty(), 0);
    }

    int ratingOrDefault() {
        return rating.map(Rating::value).orElse(UNRATED);
    }
}
