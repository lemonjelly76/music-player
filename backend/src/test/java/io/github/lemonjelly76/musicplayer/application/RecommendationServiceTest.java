package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.RecommendationService.Dropped;
import io.github.lemonjelly76.musicplayer.application.RecommendationService.PickRequest;
import io.github.lemonjelly76.musicplayer.application.RecommendationService.Saved;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
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
    void keepsPicksTheServiceCarriesAndSyncsThemAsAPlaylist() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("t-1", album("Massive Attack", "Mezzanine"));
        world.tidal.carry("t-2", album("Portishead", "Dummy"));

        Saved saved = world.recommendations.save(craig, Optional.of(running), List.of(
                pick("tidal:t-1"), pick("Portishead — Dummy"), pick("Made Up — Never Was")));

        assertThat(saved.list().name()).isEqualTo("AI Picks — Running");
        assertThat(saved.list().picks()).extracting(found -> found.album().title()).containsExactly("Mezzanine", "Dummy");
        assertThat(saved.dropped()).containsExactly(new Dropped("Made Up — Never Was", "Made Up — Never Was is not on tidal"));
        assertThat(saved.sync()).isEqualTo("synced to tidal as AI Picks — Running");
        assertThat(saved.list().playlist()).contains(new SyncedPlaylist(Provider.TIDAL, "playlist-1"));
        assertThat(world.tidal.playlistCalls).containsExactly("AI Picks — Running [t-1, t-2]");
        assertThat(world.recommendationRepository.findById(saved.list().id()).orElseThrow().playlist()).isPresent();
    }

    @Test
    void replacesTheCategorysPreviousPlaylist() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("t-1", album("Massive Attack", "Mezzanine"));
        world.tidal.carry("t-2", album("Portishead", "Dummy"));
        world.recommendations.save(craig, Optional.of(running), List.of(pick("tidal:t-1")));

        Saved second = world.recommendations.save(craig, Optional.of(running), List.of(pick("tidal:t-2")));

        assertThat(world.tidal.deletedPlaylists).containsExactly("playlist-1");
        assertThat(second.list().playlist().orElseThrow().playlistId()).isEqualTo("playlist-2");
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
        assertThat(saved.sync()).isEqualTo("not synced: Craig has no streaming service linked");
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
    void keepsTheListWhenTheServiceFails() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("t-1", album("Massive Attack", "Mezzanine"));
        world.tidal.failing = true;

        Saved saved = world.recommendations.save(craig, Optional.empty(), List.of(pick("tidal:t-1")));

        assertThat(saved.sync()).isEqualTo("saved but not synced: tidal is not connected");
        assertThat(world.recommendations.lists(craig, Optional.empty(), 5)).hasSize(1);
    }
}
