package io.github.lemonjelly76.musicplayer.domain.album;

import java.util.Arrays;
import java.util.Locale;

/**
 * How much a person likes an album for an activity (ADR-015). Three choices are quick to give and used
 * consistently; a finer scale invites dithering between neighbours.
 */
public enum Rating {
    DISLIKE(1, "dislike"),
    OK(2, "ok"),
    LOVE(3, "love");

    private final int value;
    private final String word;

    Rating(int value, String word) {
        this.value = value;
        this.word = word;
    }

    public int value() {
        return value;
    }

    public String word() {
        return word;
    }

    /**
     * Whether an album rated this belongs in the person's library for the activity: loved and OK do, disliked do not.
     */
    public boolean keeps() {
        return this != DISLIKE;
    }

    public static Rating of(int value) {
        return Arrays.stream(values())
                .filter(rating -> rating.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("a rating is 1 (dislike), 2 (ok) or 3 (love), got "
                        + value));
    }

    /**
     * Reads "love", "LOVE", "3", "ok", "dislike" and the like.
     */
    public static Rating parse(String text) {
        String word = text.strip().toLowerCase(Locale.ROOT);
        return switch (word) {
            case "3", "love", "loved", "like", "liked" -> LOVE;
            case "2", "ok", "okay", "fine" -> OK;
            case "1", "dislike", "disliked", "not for me" -> DISLIKE;
            default -> throw new IllegalArgumentException("Unknown rating '" + text + "'; use love, ok or dislike");
        };
    }
}
