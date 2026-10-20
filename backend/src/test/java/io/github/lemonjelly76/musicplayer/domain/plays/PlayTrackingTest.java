package io.github.lemonjelly76.musicplayer.domain.plays;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlayTrackingTest {

    private static final CategoryId RUNNING = CategoryId.random();
    private static final CategoryId FOCUS = CategoryId.random();

    @Test
    void aCategoryFollowsThePersonUnlessOverridden() {
        PlayTracking tracking = new PlayTracking(true, Map.of(FOCUS, false));

        assertThat(tracking.enabledFor(Optional.empty())).isTrue();
        assertThat(tracking.enabledFor(Optional.of(RUNNING))).isTrue();
        assertThat(tracking.enabledFor(Optional.of(FOCUS))).isFalse();
    }

    @Test
    void aCategoryCanBeTrackedWhenThePersonIsNot() {
        PlayTracking tracking = new PlayTracking(false, Map.of(RUNNING, true));

        assertThat(tracking.enabledFor(Optional.empty())).isFalse();
        assertThat(tracking.enabledFor(Optional.of(RUNNING))).isTrue();
        assertThat(tracking.anyEnabled()).isTrue();
        assertThat(new PlayTracking(false, Map.of(RUNNING, false)).anyEnabled()).isFalse();
    }
}
