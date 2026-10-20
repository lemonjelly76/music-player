package io.github.lemonjelly76.musicplayer.domain.recommendation;

import java.util.Objects;
import java.util.UUID;

public record RecommendationListId(UUID value) {

    public RecommendationListId {
        Objects.requireNonNull(value, "value");
    }

    public static RecommendationListId random() {
        return new RecommendationListId(UUID.randomUUID());
    }

    public static RecommendationListId of(String text) {
        return new RecommendationListId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
