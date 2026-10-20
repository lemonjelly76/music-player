package io.github.lemonjelly76.musicplayer.domain.plays;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Whether a person's plays are counted: their own setting, which a category can override either way.
 */
public record PlayTracking(boolean personEnabled, Map<CategoryId, Boolean> overrides) {

    public PlayTracking {
        overrides = Map.copyOf(Objects.requireNonNull(overrides, "overrides"));
    }

    public boolean enabledFor(Optional<CategoryId> category) {
        return category.map(id -> overrides.getOrDefault(id, personEnabled)).orElse(personEnabled);
    }

    /**
     * Whether anything at all is counted, so the poller can skip people who have turned everything off.
     */
    public boolean anyEnabled() {
        return personEnabled || overrides.containsValue(true);
    }
}
