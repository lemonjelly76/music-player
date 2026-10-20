package io.github.lemonjelly76.musicplayer.domain.taste;

import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a person likes and dislikes, in the model's own words, and the rules it must never break when recommending.
 */
public record TasteProfile(PersonId person, String summary, List<String> neverRecommend, Optional<Instant> updatedAt) {

    public TasteProfile {
        Objects.requireNonNull(person, "person");
        summary = Objects.requireNonNull(summary, "summary").strip();
        neverRecommend = Objects.requireNonNull(neverRecommend, "neverRecommend").stream()
                .map(String::strip)
                .filter(rule -> !rule.isEmpty())
                .distinct()
                .toList();
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static TasteProfile empty(PersonId person) {
        return new TasteProfile(person, "", List.of(), Optional.empty());
    }

    public TasteProfile rewritten(String newSummary, Optional<List<String>> newRules, Instant at) {
        return new TasteProfile(person, newSummary, newRules.orElse(neverRecommend), Optional.of(at));
    }
}
