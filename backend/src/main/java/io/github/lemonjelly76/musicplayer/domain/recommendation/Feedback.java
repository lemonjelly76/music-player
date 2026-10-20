package io.github.lemonjelly76.musicplayer.domain.recommendation;

import java.time.Instant;
import java.util.Objects;

public record Feedback(Verdict verdict, String comment, Instant at) {

    public Feedback {
        Objects.requireNonNull(verdict, "verdict");
        comment = Objects.requireNonNull(comment, "comment").strip();
        Objects.requireNonNull(at, "at");
    }
}
