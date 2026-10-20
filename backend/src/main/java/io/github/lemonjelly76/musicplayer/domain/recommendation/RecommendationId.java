package io.github.lemonjelly76.musicplayer.domain.recommendation;

import java.util.Objects;
import java.util.UUID;

public record RecommendationId(UUID value) {

    public RecommendationId {
        Objects.requireNonNull(value, "value");
    }

    public static RecommendationId random() {
        return new RecommendationId(UUID.randomUUID());
    }

    public static RecommendationId of(String text) {
        return new RecommendationId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
