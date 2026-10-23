package io.github.lemonjelly76.musicplayer.domain.taste;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a person likes and dislikes for one activity (no category is Mainstream), in the model's own words, and the
 * rules it must never break when recommending for it. Each activity's profile is its own.
 */
public record TasteProfile(PersonId person, Optional<CategoryId> category, String summary, List<String> neverRecommend,
                           Optional<Instant> updatedAt) {

    public TasteProfile {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(category, "category");
        summary = Objects.requireNonNull(summary, "summary").strip();
        neverRecommend = Objects.requireNonNull(neverRecommend, "neverRecommend").stream()
                .map(String::strip)
                .filter(rule -> !rule.isEmpty())
                .distinct()
                .toList();
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static TasteProfile empty(PersonId person, Optional<CategoryId> category) {
        return new TasteProfile(person, category, "", List.of(), Optional.empty());
    }

    public TasteProfile rewritten(String newSummary, Optional<List<String>> newRules, Instant at) {
        return new TasteProfile(person, category, newSummary, newRules.orElse(neverRecommend), Optional.of(at));
    }

    /**
     * Copied from the one profile every activity shared before each had its own, and not yet rewritten for this one.
     */
    public boolean carriedOver() {
        return updatedAt.isEmpty() && !summary.isEmpty();
    }
}
