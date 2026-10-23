package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtist;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ArtistServiceTest {

    private final World world = new World();
    private final Category running = world.categories.upsert("Running", "High energy");

    @Test
    void ratesAnArtistPerActivityMatchingTheirNameLoosely() {
        Person craig = world.tidalUser("Craig");

        world.artists.rate(craig, "The Strokes", Optional.of(running), Optional.of(Rating.LOVE), "");
        world.artists.rate(craig, "strokes", Optional.empty(), Optional.of(Rating.DISLIKE), "Too scrappy");
        world.artists.rate(craig, "Air", Optional.of(running), Optional.of(Rating.DISLIKE), "");

        assertThat(world.artistRepository.saved).hasSize(2);
        assertThat(world.artists.artistsFor(craig.id(), Optional.of(running.id()))).extracting(LibraryArtist::name)
                .containsExactly("The Strokes", "Air");
        assertThat(world.artists.lovedFor(craig.id(), Optional.of(running.id()))).extracting(LibraryArtist::name)
                .containsExactly("The Strokes");
        assertThat(world.artists.turnedDown(craig.id(), Optional.empty())).isEqualTo(Map.of("strokes", "The Strokes"));
        assertThat(world.artists.turnedDown(craig.id(), Optional.of(running.id()))).containsOnlyKeys("air");
    }

    @Test
    void takingAwayAnArtistsLastRatingTakesThemOutOfTheLibrary() {
        Person craig = world.tidalUser("Craig");
        world.artists.rate(craig, "Air", Optional.of(running), Optional.of(Rating.LOVE), "");
        world.artists.rate(craig, "Air", Optional.empty(), Optional.of(Rating.OK), "");

        assertThat(world.artists.rate(craig, "air", Optional.of(running), Optional.empty(), "")).isPresent();
        assertThat(world.artists.rate(craig, "AIR", Optional.empty(), Optional.empty(), "")).isEmpty();
        assertThat(world.artistRepository.saved).isEmpty();
    }

    @Test
    void needsAnArtistsName() {
        assertThatIllegalArgumentException().isThrownBy(() -> world.artists.rate(world.tidalUser("Craig"), " ",
                Optional.empty(), Optional.of(Rating.LOVE), ""));
    }
}
