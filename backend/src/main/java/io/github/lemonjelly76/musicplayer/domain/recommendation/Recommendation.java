package io.github.lemonjelly76.musicplayer.domain.recommendation;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.util.Objects;
import java.util.Optional;

/**
 * One album picked for a person, with the model's reason, and their verdict once they have listened.
 */
public record Recommendation(RecommendationId id, AlbumRef album, String reason, PickSource source,
                             Optional<Feedback> feedback) {

    public Recommendation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(album, "album");
        reason = Objects.requireNonNull(reason, "reason").strip();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("every pick needs a reason; " + album.label() + " has none");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(feedback, "feedback");
    }

    public static Recommendation create(AlbumRef album, String reason, PickSource source) {
        return new Recommendation(RecommendationId.random(), album, reason, source, Optional.empty());
    }

    public Recommendation withFeedback(Feedback newFeedback) {
        return new Recommendation(id, album, reason, source, Optional.of(newFeedback));
    }

    public boolean has(Verdict verdict) {
        return feedback.map(given -> given.verdict() == verdict).orElse(false);
    }
}
