package io.github.lemonjelly76.musicplayer.domain.taste;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TasteProfileTest {

    private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");

    @Test
    void rewritingKeepsTheRulesUnlessNewOnesAreGiven() {
        TasteProfile profile = TasteProfile.empty(PersonId.random(), Optional.empty())
                .rewritten("Likes warm analogue soul", Optional.of(List.of("No Christmas albums", " ", "No Christmas albums")), NOW);

        TasteProfile rewritten = profile.rewritten("Likes soul and 70s jazz-funk", Optional.empty(), NOW);

        assertThat(rewritten.neverRecommend()).containsExactly("No Christmas albums");
        assertThat(rewritten.summary()).isEqualTo("Likes soul and 70s jazz-funk");
        assertThat(rewritten.updatedAt()).contains(NOW);
    }

    @Test
    void aProfileCopiedFromTheSharedOneIsCarriedOverUntilRewritten() {
        TasteProfile copied = new TasteProfile(PersonId.random(), Optional.of(CategoryId.random()), "Warm soul",
                List.of(), Optional.empty());

        assertThat(copied.carriedOver()).isTrue();
        assertThat(copied.rewritten("Warm soul for dinner", Optional.empty(), NOW).carriedOver()).isFalse();
        assertThat(TasteProfile.empty(PersonId.random(), Optional.empty()).carriedOver()).isFalse();
    }
}
