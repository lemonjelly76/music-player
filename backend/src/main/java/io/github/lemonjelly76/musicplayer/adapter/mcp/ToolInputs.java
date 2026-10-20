package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbumId;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the loosely typed values a model sends, failing with messages that say how to get them right.
 */
final class ToolInputs {

    private ToolInputs() {
    }

    static Optional<Rating> rating(Integer value) {
        return Optional.ofNullable(value).map(Rating::new);
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

    static RecommendationId recommendationId(String text) {
        return new RecommendationId(uuid(text, "recommendation id", "get_recommendations"));
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
