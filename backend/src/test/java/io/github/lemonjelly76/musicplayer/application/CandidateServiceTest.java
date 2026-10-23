package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.RecommendationService.PickRequest;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.candidate.Candidate;
import io.github.lemonjelly76.musicplayer.domain.candidate.Seed;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.port.PlayRepository.CategorisedPlay;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.application.World.NOW;
import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
        world.library.add(craig, okComputer, Optional.empty(), Rating.LOVE, "", List.of());
        world.lastFm.suggest(okComputer, mezzanine, dummy, maxinquaye);
        return craig;
    }

    @Test
    void seedsOnlyFromLovedAlbumsAtMostEight() {
        Person craig = world.withoutService("Craig");
        world.library.add(craig, album("Blur", "13"), Optional.empty(), Rating.OK, "", List.of());
        for (int i = 0; i < 10; i++) {
            world.library.add(craig, album("Artist " + i, "Album " + i), Optional.empty(), Rating.LOVE, "", List.of());
        }

        List<Seed> seeds = world.candidates.seeds(craig, Optional.empty());

        assertThat(seeds).hasSize(CandidateService.MAX_SEEDS);
        assertThat(seeds).extracting(seed -> seed.album().artist()).doesNotContain("Blur");
    }

    @Test
    void anActivitySeedsOnlyFromWhatIsLovedThere() {
        Person craig = world.withoutService("Craig");
        world.library.add(craig, okComputer, Optional.empty(), Rating.LOVE, "", List.of());
        world.library.add(craig, dummy, Optional.of(running), Rating.LOVE, "", List.of());
        world.library.rate(craig, okComputer, Optional.of(running), Optional.of(Rating.DISLIKE), "");

        assertThat(world.candidates.seeds(craig, Optional.of(running))).extracting(Seed::album).containsExactly(dummy);
        assertThat(world.candidates.seeds(craig, Optional.of(driving))).isEmpty();
        assertThat(world.candidates.seeds(craig, Optional.empty())).extracting(Seed::album).containsExactly(okComputer);
    }

    @Test
    void offersSimilarAlbumsNotAlreadyInTheLibrary() {
        Person craig = craigWithOkComputer();
        world.library.add(craig, dummy, Optional.empty(), Rating.OK, "", List.of());

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
    void anAlbumDislikedForOneActivityCanStillComeUpInAnother() {
        Person craig = craigWithOkComputer();
        world.catalogue.know(mezzanine);
        world.recommendations.save(craig, Optional.of(running), List.of(
                new PickRequest("Massive Attack — Mezzanine", "Dark", PickSource.POOL)));
        world.library.rate(craig, mezzanine, Optional.of(running), Optional.of(Rating.DISLIKE), "too slow");
        world.library.rate(craig, okComputer, Optional.of(running), Optional.of(Rating.LOVE), "");
        world.library.rate(craig, okComputer, Optional.of(driving), Optional.of(Rating.LOVE), "");
        ageEverything();

        assertThat(world.candidates.candidates(craig, Optional.of(running), 10)).extracting(Candidate::album)
                .doesNotContain(mezzanine);
        assertThat(world.candidates.candidates(craig, Optional.of(driving), 10)).extracting(Candidate::album)
                .contains(mezzanine);
    }

    @Test
    void keepsOnlyWhatThePersonsServiceCarriesWithItsId() {
        Person craig = world.tidalUser("Craig");
        world.library.add(craig, okComputer, Optional.empty(), Rating.LOVE, "", List.of());
        world.lastFm.suggest(okComputer, mezzanine, dummy, maxinquaye);
        world.tidal.carry("t-dummy", dummy);
        world.tidal.carry("t-tricky", maxinquaye);

        List<Candidate> pool = world.candidates.candidates(craig, Optional.empty(), 1);

        assertThat(pool).extracting(candidate -> candidate.album().ids().tidal().orElseThrow())
                .containsExactly("t-dummy");
    }

    @Test
    void keepsWhatWasCheckedWhenTheServiceFailsPartWay() {
        Person craig = world.tidalUser("Craig");
        world.library.add(craig, okComputer, Optional.empty(), Rating.LOVE, "", List.of());
        world.lastFm.suggest(okComputer, mezzanine, dummy, maxinquaye);
        world.tidal.carry("t-mezz", mezzanine);
        world.tidal.carry("t-dummy", dummy);
        world.tidal.findCalls = 0;
        world.tidal.findsBeforeFailing = 1;

        assertThat(world.candidates.candidates(craig, Optional.empty(), 3)).extracting(Candidate::album)
                .extracting(AlbumRef::title).containsExactly("Mezzanine");
    }

    @Test
    void saysSoWhenTheServiceFailsBeforeAnythingIsChecked() {
        Person craig = world.tidalUser("Craig");
        world.library.add(craig, okComputer, Optional.empty(), Rating.LOVE, "", List.of());
        world.lastFm.suggest(okComputer, mezzanine);
        world.tidal.findCalls = 0;
        world.tidal.findsBeforeFailing = 0;

        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> world.candidates.candidates(craig, Optional.empty(), 3));
    }

    @Test
    void addsTheServicesOwnSimilarAlbums() {
        Person craig = world.tidalUser("Craig");
        AlbumRef onTidal = world.tidal.carry("t-ok", okComputer);
        world.library.add(craig, onTidal, Optional.empty(), Rating.LOVE, "", List.of());
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

        assertThat(world.candidates.moreLikeThis(craig, mezzanine, Optional.empty(), 5)).extracting(Candidate::album)
                .containsExactly(okComputer, dummy);
    }

    private void ageEverything() {
        world.recommendationRepository.saved.replaceAll((id, list) -> new RecommendationList(list.id(), list.person(),
                list.category(), list.name(), NOW.minus(Duration.ofDays(60)), list.playlist(), list.picks()));
    }

    @Test
    void alsoSeedsFromTheAlbumsOfSongsLovedThereAndWhatIsOnRepeatThere() {
        Person craig = world.tidalUser("Craig");
        world.library.add(craig, okComputer, Optional.of(running), Rating.LOVE, "", List.of());
        world.library.add(craig, maxinquaye, Optional.of(running), Rating.DISLIKE, "", List.of());
        world.songs.rate(craig, new Track(Provider.TIDAL, "s1", "Air", "Sexy Boy", "Moon Safari", Optional.empty()),
                Optional.of(running), Optional.of(Rating.LOVE));
        world.songs.rate(craig, new Track(Provider.TIDAL, "s2", "Bonobo", "Kerala", "Migration", Optional.empty()),
                Optional.empty(), Optional.of(Rating.LOVE));
        world.playRepository.add(craig.id(), List.of(played(dummy, running), played(dummy, running),
                played(maxinquaye, running), played(mezzanine, driving)));

        assertThat(world.candidates.seeds(craig, Optional.of(running))).extracting(Seed::label).containsExactly(
                "Radiohead — OK Computer", "Portishead — Dummy (on repeat)", "Air — Moon Safari (a loved song's album)");
    }

    @Test
    void offersAnAlbumOnceWhenTwoOfItsTitlesFindItOnTheService() {
        Person craig = world.tidalUser("Craig");
        world.library.add(craig, okComputer, Optional.empty(), Rating.LOVE, "", List.of());
        AlbumRef submarine = album("Alex Turner", "Submarine (Original Songs)");
        world.lastFm.suggest(okComputer, submarine, album("Alex Turner", "Submarine - Songs from the Film"));
        world.tidal.carry("t-sub", submarine);
        world.tidal.aliases.put(AlbumKey.of("Alex Turner", "Submarine - Songs from the Film"), "t-sub");

        assertThat(world.candidates.candidates(craig, Optional.empty(), 5)).singleElement()
                .satisfies(candidate -> assertThat(candidate.album().ids().tidal()).contains("t-sub"));
    }

    @Test
    void ranksDownWhatIsLikeAlbumsTurnedDownThere() {
        Person craig = craigWithOkComputer();
        AlbumRef riot = album("Paramore", "Riot!");
        world.library.add(craig, riot, Optional.empty(), Rating.DISLIKE, "", List.of());
        world.lastFm.suggest(riot, mezzanine);

        List<Candidate> pool = world.candidates.candidates(craig, Optional.empty(), 10);

        assertThat(pool).extracting(Candidate::album).containsExactly(dummy, maxinquaye, mezzanine);
        assertThat(pool.getLast().cautions()).containsExactly("like Paramore — Riot!, turned down here");
    }

    private static CategorisedPlay played(AlbumRef album, Category activity) {
        return new CategorisedPlay(new Play(album, NOW.minusSeconds(3600), "spotify", Optional.empty()),
                Optional.of(activity.id()));
    }
}
