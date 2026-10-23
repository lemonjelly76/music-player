package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.RecommendationService.Dropped;
import io.github.lemonjelly76.musicplayer.application.RecommendationService.PickRequest;
import io.github.lemonjelly76.musicplayer.application.RecommendationService.Saved;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RecommendationServiceTest {

    private final World world = new World();
    private final Category running = world.categories.upsert("Running", "High energy");

    private static PickRequest pick(String reference) {
        return new PickRequest(reference, "Because", PickSource.POOL);
    }

    @Test
    void keepsPicksTheServiceCarriesWithoutWritingAPlaylist() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("t-1", album("Massive Attack", "Mezzanine"));
        world.tidal.carry("t-2", album("Portishead", "Dummy"));

        Saved saved = world.recommendations.save(craig, Optional.of(running), List.of(
                pick("tidal:t-1"), pick("Portishead — Dummy"), pick("Made Up — Never Was")));

        assertThat(saved.list().name()).isEqualTo("AI Picks — Running");
        assertThat(saved.list().picks()).extracting(found -> found.album().title()).containsExactly("Mezzanine", "Dummy");
        assertThat(saved.dropped()).containsExactly(new Dropped("Made Up — Never Was", "Made Up — Never Was is not on tidal"));
        assertThat(saved.list().playlist()).isEmpty();
        assertThat(world.tidal.playlistCalls).isEmpty();
    }

    @Test
    void dropsAlbumsInTheLibraryAndRepeats() {
        Person craig = world.withoutService("Craig");
        AlbumRef mezzanine = world.catalogue.know(album("Massive Attack", "Mezzanine"));
        world.catalogue.know(album("Portishead", "Dummy"));
        world.library.add(craig, mezzanine, Optional.empty(), Rating.LOVE, "", List.of());

        Saved saved = world.recommendations.save(craig, Optional.empty(), List.of(
                pick("Massive Attack — Mezzanine"), pick("Portishead — Dummy"), pick("Portishead - Dummy")));

        assertThat(saved.list().picks()).hasSize(1);
        assertThat(saved.dropped()).extracting(Dropped::why).containsExactly("already rated", "already in this list");
    }

    @Test
    void dropsAnAlbumNamedFromTheLibraryWithoutLookingItUp() {
        Person craig = world.withoutService("Craig");
        world.library.add(craig, album("Radiohead", "OK Computer"), Optional.empty(), Rating.LOVE, "", List.of());
        world.catalogue.know(album("Portishead", "Dummy"));

        Saved saved = world.recommendations.save(craig, Optional.empty(), List.of(
                pick("Radiohead — OK Computer"), pick("Portishead — Dummy")));

        assertThat(saved.dropped()).containsExactly(
                new Dropped("Radiohead — OK Computer", "already rated"));
    }

    @Test
    void dropsAlbumsDislikedForTheActivityButNotForAnother() {
        Person craig = world.withoutService("Craig");
        world.catalogue.know(album("Portishead", "Dummy"));
        world.catalogue.know(album("Air", "Moon Safari"));
        world.library.rate(craig, "Portishead — Dummy", Optional.of(running), Optional.of(Rating.DISLIKE), "");

        Saved forRunning = world.recommendations.save(craig, Optional.of(running), List.of(
                pick("Portishead — Dummy"), pick("Air — Moon Safari")));
        Saved forGeneral = world.recommendations.save(craig, Optional.empty(), List.of(pick("Portishead — Dummy")));

        assertThat(forRunning.dropped()).extracting(Dropped::why).containsExactly("already rated");
        assertThat(forGeneral.dropped()).isEmpty();
    }

    @Test
    void refusesAListWithNothingLeftInIt() {
        Person craig = world.withoutService("Craig");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> world.recommendations.save(craig, Optional.empty(), List.of(pick("Made Up — Never Was"))))
                .withMessageContaining("Made Up — Never Was");
    }

    @Test
    void deletesAList() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("t-1", album("Massive Attack", "Mezzanine"));
        RecommendationList list = world.recommendations.save(craig, Optional.empty(), List.of(pick("tidal:t-1"))).list();

        world.recommendations.delete(craig, list.id());

        assertThat(world.recommendations.history(craig, 10)).isEmpty();
    }

    @Test
    void removesOnePickAndTheListWithItsLast() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("t-1", album("Massive Attack", "Mezzanine"));
        world.tidal.carry("t-2", album("Portishead", "Dummy"));
        RecommendationList list = world.recommendations.save(craig, Optional.empty(),
                List.of(pick("tidal:t-1"), pick("tidal:t-2"))).list();

        world.recommendations.removePick(craig, list.id(), list.picks().getFirst().id());

        assertThat(world.recommendations.history(craig, 10)).singleElement()
                .satisfies(left -> assertThat(left.picks()).extracting(found -> found.album().title())
                        .containsExactly("Dummy"));

        world.recommendations.removePick(craig, list.id(), list.picks().getLast().id());

        assertThat(world.recommendations.history(craig, 10)).isEmpty();
    }

    @Test
    void cannotDeleteSomeoneElsesList() {
        Person craig = world.tidalUser("Craig");
        Person robin = world.tidalUser("Robin");
        world.tidal.carry("t-1", album("Massive Attack", "Mezzanine"));
        RecommendationList theirs = world.recommendations.save(robin, Optional.empty(), List.of(pick("tidal:t-1"))).list();

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> world.recommendations.delete(craig, theirs.id()));
        assertThat(world.recommendations.history(robin, 10)).hasSize(1);
    }
}
