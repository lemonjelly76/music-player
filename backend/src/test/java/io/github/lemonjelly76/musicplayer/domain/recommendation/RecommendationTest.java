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
                .isEqualTo("AI Picks");
        assertThat(RecommendationList.create(PersonId.random(), Optional.of(running), List.of(), NOW).name())
                .isEqualTo("AI Picks — Running");
    }

    @Test
    void feedbackIsKeptOnThePick() {
        Recommendation pick = Recommendation.create(AlbumRef.of("Talk Talk", "Spirit of Eden"), "Slow-building",
                PickSource.WILDCARD);

        Recommendation judged = pick.withFeedback(new Feedback(Verdict.NOT_FOR_ME, "too sparse", NOW));

        assertThat(judged.has(Verdict.NOT_FOR_ME)).isTrue();
        assertThat(pick.has(Verdict.NOT_FOR_ME)).isFalse();
    }

    @Test
    void verdictsAndSourcesParseLoosely() {
        assertThat(Verdict.parse("not for me")).isEqualTo(Verdict.NOT_FOR_ME);
        assertThat(Verdict.parse("already-know")).isEqualTo(Verdict.ALREADY_KNOW);
        assertThat(PickSource.parse("Wildcard")).isEqualTo(PickSource.WILDCARD);
        assertThatIllegalArgumentException().isThrownBy(() -> Verdict.parse("meh"))
                .withMessageContaining("LIKED, NOT_FOR_ME, ALREADY_KNOW");
    }
}
