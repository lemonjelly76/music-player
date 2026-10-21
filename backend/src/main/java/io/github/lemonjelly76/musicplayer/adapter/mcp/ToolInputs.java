package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbumId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the loosely typed values a model sends, failing with messages that say how to get them right.
 */
final class ToolInputs {

    private ToolInputs() {
    }

    /**
     * A rating as the model writes it (love, ok, dislike or 1 to 3); "none" or nothing means no rating.
     */
    static Optional<Rating> rating(String text) {
        return text == null || text.isBlank() || text.strip().equalsIgnoreCase("none")
                ? Optional.empty()
                : Optional.of(Rating.parse(text));
    }

    static int limit(Integer requested, int defaultLimit, int max) {
        int limit = requested == null ? defaultLimit : requested;
        if (limit < 1 || limit > max) {
            throw new IllegalArgumentException("limit must be between 1 and " + max + ", got " + limit);
        }
        return limit;
    }

    static List<String> tags(List<String> tags) {
        return tags == null ? List.of() : tags;
    }

    static String text(String value) {
        return value == null ? "" : value;
    }

    static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    static LibraryAlbumId libraryAlbumId(String text) {
        return new LibraryAlbumId(uuid(text, "library album id", "get_library"));
    }

    private static UUID uuid(String text, String name, String source) {
        try {
            return UUID.fromString(String.valueOf(text).strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + text + "' is not a valid " + name + "; use an id returned by "
                    + source, e);
        }
    }
}
