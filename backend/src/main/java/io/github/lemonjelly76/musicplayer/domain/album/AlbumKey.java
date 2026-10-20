package io.github.lemonjelly76.musicplayer.domain.album;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifies an album across catalogues by artist and title, so "The Beatles – Abbey Road (Remastered 2019)" on one
 * service matches "Beatles – Abbey Road" on another.
 */
public record AlbumKey(String value) {

    private static final Pattern EDITION = Pattern.compile(
            "[(\\[][^)\\]]*(remaster|deluxe|edition|expanded|anniversary|version|bonus|mono|stereo|reissue)[^)\\]]*[)\\]]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    public AlbumKey {
        Objects.requireNonNull(value, "value");
    }

    public static AlbumKey of(String artist, String title) {
        return new AlbumKey(normalise(artist) + "|" + normalise(EDITION.matcher(title).replaceAll("")));
    }

    private static String normalise(String text) {
        String plain = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        String words = NOT_WORD.matcher(plain.toLowerCase(Locale.ROOT).replace("&", " and ")).replaceAll(" ").strip();
        return words.startsWith("the ") ? words.substring(4) : words;
    }

    @Override
    public String toString() {
        return value;
    }
}
