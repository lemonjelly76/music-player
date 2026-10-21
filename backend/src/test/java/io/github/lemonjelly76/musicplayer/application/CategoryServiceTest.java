package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.category.Category;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CategoryServiceTest {

    private final World world = new World();

    @Test
    void removesAnActivityByNameOrId() {
        Category explore = world.categories.upsert("Dinner Explore", "");
        Category running = world.categories.upsert("Running", "");

        world.categories.remove("dinner explore");
        world.categories.remove(running.id().toString());

        assertThat(world.categories.all()).isEmpty();
        assertThat(explore.name()).isEqualTo("Dinner Explore");
    }

    @Test
    void anUnknownActivityIsNotFound() {
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> world.categories.remove("Nope"));
    }

    @Test
    void renamesAnActivityKeepingItsRules() {
        Category working = world.categories.upsert("Working", "Instrumental, steady");

        Category focus = world.categories.rename(working.id().toString(), "  Focus ");

        assertThat(focus).isEqualTo(new Category(working.id(), "Focus", "Instrumental, steady"));
        assertThat(world.categories.all()).containsExactly(focus);
    }

    @Test
    void refusesANameThatIsTakenOrMeansGeneral() {
        world.categories.upsert("Running", "");
        Category working = world.categories.upsert("Working", "");

        assertThatIllegalArgumentException().isThrownBy(() -> world.categories.rename("Working", "running"))
                .withMessageContaining("already an activity called Running");
        assertThatIllegalArgumentException().isThrownBy(() -> world.categories.rename("Working", "General"));
        assertThatIllegalArgumentException().isThrownBy(() -> world.categories.rename("Working", " "));
        assertThat(world.categories.rename("Working", "WORKING").name()).isEqualTo("WORKING");
        assertThat(working.name()).isEqualTo("Working");
    }
}
