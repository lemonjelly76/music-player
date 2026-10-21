package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.category.Category;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
}
