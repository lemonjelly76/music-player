package io.github.lemonjelly76.musicplayer.domain.album;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Artist, album and song names reduced to what identifies them across catalogues, so "The Beatles – Abbey Road
 * (Remastered 2019)" on one service matches "Beatles – Abbey Road" on another.
 */
public final class Names {

    private static final Pattern EDITION = Pattern.compile(
            "[(\\[][^)\\]]*(remaster|deluxe|edition|expanded|anniversary|version|bonus|mono|stereo|reissue)[^)\\]]*[)\\]]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GUESTS = Pattern.compile("\\s*[(\\[](feat\\.?|ft\\.?|featuring|with) [^)\\]]*[)\\]]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private Names() {
    }

    /**
     * Lower case words without accents or punctuation, "&" as "and" and no leading "the".
     */
    public static String normalise(String text) {
        String plain = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        String words = NOT_WORD.matcher(plain.toLowerCase(Locale.ROOT).replace("&", " and ")).replaceAll(" ").strip();
        return words.startsWith("the ") ? words.substring(4) : words;
    }

    /**
     * A song's title without its bracketed guests, "Song (feat. Someone)" being "Song", as catalogues credit guests
     * differently.
     */
    public static String withoutGuests(String title) {
        return GUESTS.matcher(title).replaceAll("").strip();
    }

    /**
     * A title normalised without bracketed edition notes such as "(Deluxe Edition)".
     */
    public static String title(String title) {
        return normalise(EDITION.matcher(title).replaceAll(""));
    }
}
