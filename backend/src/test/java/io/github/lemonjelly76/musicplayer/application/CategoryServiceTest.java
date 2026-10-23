package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.category.Category;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void refusesANameThatIsTakenOrMeansMainstream() {
        world.categories.upsert("Running", "");
        Category working = world.categories.upsert("Working", "");

        assertThatIllegalArgumentException().isThrownBy(() -> world.categories.rename("Working", "running"))
                .withMessageContaining("already an activity called Running");
        assertThatIllegalArgumentException().isThrownBy(() -> world.categories.rename("Working", "Mainstream"))
                .withMessage("Mainstream is always there; choose another name");
        assertThatIllegalArgumentException().isThrownBy(() -> world.categories.rename("Working", "General"));
        assertThatIllegalArgumentException().isThrownBy(() -> world.categories.upsert("mainstream", ""));
        assertThat(world.categories.findOptional("Mainstream")).isEmpty();
        assertThat(world.categories.findOptional("General")).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> world.categories.rename("Working", " "));
        assertThat(world.categories.rename("Working", "WORKING").name()).isEqualTo("WORKING");
        assertThat(working.name()).isEqualTo("Working");
    }

    @Test
    void keepsActivitiesInTheOrderGivenWithNewOnesLast() {
        Category running = world.categories.upsert("Running", "");
        Category dinner = world.categories.upsert("Dinner", "");
        Category chill = world.categories.upsert("Chill", "");

        world.categories.reorder(List.of(dinner.id().toString(), chill.id().toString(), running.id().toString()));
        Category focus = world.categories.upsert("Focus", "");

        assertThat(world.categories.all()).containsExactly(dinner, chill, running, focus);
    }

    @Test
    void anOrderMustNameEveryActivityOnce() {
        Category running = world.categories.upsert("Running", "");
        Category dinner = world.categories.upsert("Dinner", "");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> world.categories.reorder(List.of(running.id().toString())));
        assertThatIllegalArgumentException().isThrownBy(() -> world.categories.reorder(
                List.of(running.id().toString(), running.id().toString(), dinner.id().toString())));
    }
}
