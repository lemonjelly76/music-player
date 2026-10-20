package io.github.lemonjelly76.musicplayer.domain.album;

public record Rating(int value) {

    public static final int MIN = 1;
    public static final int MAX = 5;

    public Rating {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("a rating runs from " + MIN + " to " + MAX + ", got " + value);
        }
    }
}
