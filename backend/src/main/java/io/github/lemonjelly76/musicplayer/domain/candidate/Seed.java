package io.github.lemonjelly76.musicplayer.domain.candidate;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.util.Objects;

/**
 * An album the search for new music starts from, with how often the person plays it: one they love for the
 * activity, the album of a song they love there, or one they keep playing there.
 */
public record Seed(AlbumRef album, int plays, Origin origin) {

    public enum Origin {
        LOVED(""), LOVED_SONG(" (a loved song's album)"), ON_REPEAT(" (on repeat)");

        private final String note;

        Origin(String note) {
            this.note = note;
        }
    }

    public Seed {
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(origin, "origin");
        if (plays < 0) {
            throw new IllegalArgumentException("plays cannot be negative");
        }
    }

    public Seed(AlbumRef album, int plays) {
        this(album, plays, Origin.LOVED);
    }

    public static Seed of(AlbumRef album) {
        return new Seed(album, 0);
    }

    /**
     * "Air — Moon Safari", and where it came from when it is not a loved album.
     */
    public String label() {
        return album.label() + origin.note;
    }
}
