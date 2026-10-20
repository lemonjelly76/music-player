package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.RecommendationService.PickRequest;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.candidate.Candidate;
import io.github.lemonjelly76.musicplayer.domain.candidate.Seed;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Verdict;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.application.World.NOW;
import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;

class CandidateServiceTest {

    private final World world = new World();
    private final Category running = world.categories.upsert("Running", "High energy");
    private final Category driving = world.categories.upsert("Driving", "Steady");

    private final AlbumRef okComputer = album("Radiohead", "OK Computer");
    private final AlbumRef mezzanine = album("Massive Attack", "Mezzanine");
    private final AlbumRef dummy = album("Portishead", "Dummy");
    private final AlbumRef maxinquaye = album("Tricky", "Maxinquaye");

    private Person craigWithOkComputer() {
        Person craig = world.withoutService("Craig");
        world.library.add(craig, okComputer, Optional.of(new Rating(5)), "", List.of());
        world.lastFm.suggest(okComputer, mezzanine, dummy, maxinquaye);
        return craig;
    }

    @Test
    void seedsFromTheBestRatedAlbumsAndTheMostPlayed() {
        Person craig = world.withoutService("Craig");
        for (int i = 0; i < 10; i++) {
            world.library.add(craig, album("Artist " + i, "Album " + i), Optional.of(new Rating(i < 2 ? 5 : 3)), "",
                    List.of());
        }

        List<Seed> seeds = world.candidates.seeds(craig, Optional.empty());

        assertThat(seeds).hasSize(CandidateService.MAX_SEEDS);
        assertThat(seeds.subList(0, 2)).extracting(seed -> seed.rating().orElseThrow().value()).containsOnly(5);
    }

    @Test
    void offersSimilarAlbumsNotAlreadyInTheLibrary() {
        Person craig = craigWithOkComputer();
        world.library.add(craig, dummy, Optional.empty(), "", List.of());

        List<Candidate> pool = world.candidates.candidates(craig, Optional.empty(), 10);

        assertThat(pool).extracting(Candidate::album).containsExactly(mezzanine, maxinquaye);
        assertThat(pool.getFirst().seeds()).containsExactly("Radiohead — OK Computer");
    }

    @Test
    void leavesOutRecentPicksAndAlbumsTheyKnow() {
        Person craig = craigWithOkComputer();
        world.catalogue.know(mezzanine);
        world.catalogue.know(dummy);
        world.recommendations.save(craig, Optional.empty(), List.of(
                new PickRequest("Massive Attack — Mezzanine", "Dark", PickSource.POOL)));

        assertThat(world.candidates.candidates(craig, Optional.empty(), 10)).extracting(Candidate::album)
                .containsExactly(dummy, maxinquaye);
    }

    @Test
    void anAlbumTurnedDownForOneCategoryCanStillComeUpInAnother() {
        Person craig = craigWithOkComputer();
        world.catalogue.know(mezzanine);
        RecommendationList runningList = world.recommendations.save(craig, Optional.of(running), List.of(
                new PickRequest("Massive Attack — Mezzanine", "Dark", PickSource.POOL))).list();
        world.recommendations.feedback(runningList.picks().getFirst().id(), Verdict.NOT_FOR_ME, "too slow");
        ageEverything();

        assertThat(world.candidates.candidates(craig, Optional.of(running), 10)).extracting(Candidate::album)
                .doesNotContain(mezzanine);
        assertThat(world.candidates.candidates(craig, Optional.of(driving), 10)).extracting(Candidate::album)
                .contains(mezzanine);
    }

    @Test
    void keepsOnlyWhatThePersonsServiceCarriesWithItsId() {
        Person craig = world.tidalUser("Craig");
        world.library.add(craig, okComputer, Optional.of(new Rating(5)), "", List.of());
        world.lastFm.suggest(okComputer, mezzanine, dummy, maxinquaye);
        world.tidal.carry("t-dummy", dummy);
        world.tidal.carry("t-tricky", maxinquaye);

        List<Candidate> pool = world.candidates.candidates(craig, Optional.empty(), 1);

        assertThat(pool).extracting(candidate -> candidate.album().ids().tidal().orElseThrow())
                .containsExactly("t-dummy");
    }

    @Test
    void addsTheServicesOwnSimilarAlbums() {
        Person craig = world.tidalUser("Craig");
        AlbumRef onTidal = world.tidal.carry("t-ok", okComputer);
        world.library.add(craig, onTidal, Optional.of(new Rating(5)), "", List.of());
        AlbumRef kidA = world.tidal.carry("t-kida", album("Radiohead", "Kid A"));
        world.tidal.similar.put("t-ok", List.of(kidA));

        assertThat(world.candidates.candidates(craig, Optional.empty(), 5))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.sources()).containsExactly("tidal"));
    }

    @Test
    void moreLikeThisStartsFromOneAlbumAndLeavesItOut() {
        Person craig = world.withoutService("Craig");
        world.lastFm.suggest(mezzanine, okComputer, dummy);

        assertThat(world.candidates.moreLikeThis(craig, mezzanine, 5)).extracting(Candidate::album)
                .containsExactly(okComputer, dummy);
    }

    private void ageEverything() {
        world.recommendationRepository.saved.replaceAll((id, list) -> new RecommendationList(list.id(), list.person(),
                list.category(), list.name(), NOW.minus(Duration.ofDays(60)), list.playlist(), list.picks()));
    }
}
