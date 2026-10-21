package io.github.lemonjelly76.musicplayer.domain.recommendation;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.util.Objects;

/**
 * One album picked for a person, with the model's reason. What they think of it is a rating on the album in their
 * library (ADR-015), not part of the pick.
 */
public record Recommendation(RecommendationId id, AlbumRef album, String reason, PickSource source) {

    public Recommendation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(album, "album");
        reason = Objects.requireNonNull(reason, "reason").strip();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("every pick needs a reason; " + album.label() + " has none");
        }
        Objects.requireNonNull(source, "source");
    }

    public static Recommendation create(AlbumRef album, String reason, PickSource source) {
        return new Recommendation(RecommendationId.random(), album, reason, source);
    }
}
