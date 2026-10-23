package io.github.lemonjelly76.musicplayer.domain.recommendation;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RecommendationTest {

    private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");

    @Test
    void everyPickNeedsAReason() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Recommendation.create(AlbumRef.of("Talk Talk", "Spirit of Eden"), " ", PickSource.POOL))
                .withMessageContaining("Talk Talk — Spirit of Eden");
    }

    @Test
    void listsAreNamedSoVoiceAssistantsCanFindThem() {
        Category running = Category.create("Running", "High energy");

        assertThat(RecommendationList.create(PersonId.random(), Optional.empty(), List.of(), NOW).name())
                .isEqualTo("AI Picks — Mainstream");
        assertThat(RecommendationList.create(PersonId.random(), Optional.of(running), List.of(), NOW).name())
                .isEqualTo("AI Picks — Running");
    }

    @Test
    void sourcesParseLoosely() {
        assertThat(PickSource.parse("Wildcard")).isEqualTo(PickSource.WILDCARD);
        assertThatIllegalArgumentException().isThrownBy(() -> PickSource.parse("meh"));
    }
}
