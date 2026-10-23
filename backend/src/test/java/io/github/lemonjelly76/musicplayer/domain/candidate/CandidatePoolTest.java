package io.github.lemonjelly76.musicplayer.domain.candidate;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CandidatePoolTest {

    private static final Seed LOVED = Seed.of(AlbumRef.of("Radiohead", "OK Computer"));
    private static final Seed ALSO_LOVED = Seed.of(AlbumRef.of("Blur", "13"));

    private static final AlbumRef MEZZANINE = AlbumRef.of("Massive Attack", "Mezzanine");
    private static final AlbumRef DUMMY = AlbumRef.of("Portishead", "Dummy");

    @Test
    void albumsSeveralSourcesAgreeOnRankFirst() {
        List<Candidate> ranked = CandidatePool.rank(List.of(
                new Suggestion(DUMMY, "lastfm", LOVED),
                new Suggestion(MEZZANINE, "lastfm", LOVED),
                new Suggestion(MEZZANINE.withIds(ProviderIds.of(Provider.TIDAL, "t-1")), "tidal", LOVED)), Set.of());

        assertThat(ranked).extracting(candidate -> candidate.album().title()).containsExactly("Mezzanine", "Dummy");
        Candidate top = ranked.getFirst();
        assertThat(top.sources()).containsExactly("lastfm", "tidal");
        assertThat(top.album().ids().tidal()).contains("t-1");
        assertThat(top.seeds()).containsExactly("Radiohead — OK Computer");
        assertThat(top.score()).isEqualTo(2 * 2 + 1);
    }

    @Test
    void severalLovedAlbumsLeadingToOneOutrankASingleOne() {
        List<Candidate> ranked = CandidatePool.rank(List.of(
                new Suggestion(DUMMY, "lastfm", LOVED),
                new Suggestion(MEZZANINE, "lastfm", LOVED),
                new Suggestion(MEZZANINE, "lastfm", ALSO_LOVED)), Set.of());

        assertThat(ranked).extracting(candidate -> candidate.album().title()).containsExactly("Mezzanine", "Dummy");
        assertThat(ranked.getFirst().score()).isEqualTo(2 + 2);
    }

    @Test
    void playsOfTheSeedAddWeight() {
        Seed replayed = new Seed(AlbumRef.of("Blur", "13"), 15);

        List<Candidate> ranked = CandidatePool.rank(List.of(
                new Suggestion(DUMMY, "lastfm", ALSO_LOVED),
                new Suggestion(MEZZANINE, "lastfm", replayed)), Set.of());

        assertThat(ranked.getFirst().album()).isEqualTo(MEZZANINE);
        assertThat(ranked.getFirst().score()).isEqualTo(2 + 1 + 0.5 * 4);
    }

    @Test
    void dropsExcludedAlbumsMatchedLoosely() {
        Set<AlbumKey> excluded = Set.of(AlbumKey.of("massive attack", "Mezzanine (Remastered)"));

        List<Candidate> ranked = CandidatePool.rank(List.of(
                new Suggestion(DUMMY, "lastfm", LOVED),
                new Suggestion(MEZZANINE, "lastfm", LOVED)), excluded);

        assertThat(ranked).extracting(Candidate::album).containsExactly(DUMMY);
    }

    @Test
    void mergesOneAlbumFoundUnderTwoTitlesByItsServiceId() {
        ProviderIds submarine = ProviderIds.of(Provider.TIDAL, "t-5866723");
        List<Candidate> ranked = CandidatePool.rank(List.of(
                new Suggestion(new AlbumRef("Alex Turner", "Submarine (Original Songs)", java.util.Optional.empty(),
                        submarine), "tidal", LOVED),
                new Suggestion(new AlbumRef("Alex Turner", "Submarine - Original Songs from the Film",
                        java.util.Optional.empty(), submarine), "lastfm", ALSO_LOVED)), Set.of());

        assertThat(ranked).singleElement().satisfies(merged -> {
            assertThat(merged.sources()).containsExactly("lastfm", "tidal");
            assertThat(merged.seeds()).hasSize(2);
        });
    }

    @Test
    void leavesOutMixesOuttakesRemixAndLiveAlbumsAndBestOfs() {
        List<Candidate> ranked = CandidatePool.rank(List.of(
                new Suggestion(AlbumRef.of("Maribou State", "fabric presents Maribou State (DJ Mix)"), "tidal", LOVED),
                new Suggestion(AlbumRef.of("Maribou State", "Portraits Outtakes"), "lastfm", LOVED),
                new Suggestion(AlbumRef.of("Catching Flies", "Silver Linings Remixed"), "lastfm", LOVED),
                new Suggestion(AlbumRef.of("Bonobo", "Live at the Roundhouse"), "lastfm", LOVED),
                new Suggestion(AlbumRef.of("Air", "Twentyears (Best Of)"), "lastfm", LOVED),
                new Suggestion(AlbumRef.of("Maribou State", "Portraits"), "lastfm", LOVED)), Set.of());

        assertThat(ranked).extracting(candidate -> candidate.album().title()).containsExactly("Portraits");
    }

    @Test
    void keepsAtMostTwoAlbumsByOneArtist() {
        List<Candidate> ranked = CandidatePool.rank(List.of(
                new Suggestion(AlbumRef.of("Maribou State", "Portraits"), "lastfm", LOVED),
                new Suggestion(AlbumRef.of("Maribou State", "Portraits"), "tidal", LOVED),
                new Suggestion(AlbumRef.of("Maribou State", "Hallucinating Love"), "lastfm", LOVED),
                new Suggestion(AlbumRef.of("maribou state", "Kingdoms in Colour"), "lastfm", LOVED),
                new Suggestion(DUMMY, "lastfm", LOVED)), Set.of());

        assertThat(ranked).extracting(candidate -> candidate.album().title())
                .containsExactly("Portraits", "Hallucinating Love", "Dummy");
    }

    @Test
    void takesTurnsBetweenSeedsSoOneLovedAlbumCannotFillThePool() {
        List<Candidate> ranked = CandidatePool.rank(List.of(
                new Suggestion(AlbumRef.of("A", "One"), "lastfm", LOVED),
                new Suggestion(AlbumRef.of("B", "Two"), "lastfm", LOVED),
                new Suggestion(AlbumRef.of("C", "Three"), "lastfm", LOVED),
                new Suggestion(AlbumRef.of("D", "Four"), "lastfm", ALSO_LOVED)), Set.of());

        assertThat(ranked).extracting(candidate -> candidate.album().title()).containsExactly("One", "Four", "Two", "Three");
    }

    @Test
    void ranksDownArtistsTurnedDownAndAlbumsLikeOnesTurnedDownAndSaysSo() {
        Seed turnedDown = Seed.of(AlbumRef.of("Paramore", "Riot!"));
        List<Candidate> ranked = CandidatePool.rank(List.of(
                new Suggestion(AlbumRef.of("Fall Out Boy", "Infinity on High"), "lastfm", LOVED),
                new Suggestion(AlbumRef.of("Jimmy Eat World", "Futures"), "lastfm", LOVED),
                new Suggestion(DUMMY, "lastfm", LOVED)), Set.of(),
                new TurnedDown(Set.of(), Set.of("Jimmy Eat World"),
                        List.of(new Suggestion(AlbumRef.of("Fall Out Boy", "Infinity on High"), "lastfm", turnedDown))));

        assertThat(ranked).extracting(candidate -> candidate.album().title())
                .containsExactly("Dummy", "Infinity on High", "Futures");
        assertThat(ranked.get(1).cautions()).containsExactly("like Paramore — Riot!, turned down here");
        assertThat(ranked.get(2).cautions()).containsExactly("by an artist with an album turned down here");
    }

    @Test
    void saysWhereASeedOtherThanALovedAlbumCameFrom() {
        Seed song = new Seed(AlbumRef.of("Air", "Moon Safari"), 0, Seed.Origin.LOVED_SONG);
        Seed played = new Seed(AlbumRef.of("Bonobo", "Migration"), 12, Seed.Origin.ON_REPEAT);

        List<Candidate> ranked = CandidatePool.rank(List.of(
                new Suggestion(DUMMY, "lastfm", song), new Suggestion(DUMMY, "tidal", played)), Set.of());

        assertThat(ranked.getFirst().seeds())
                .containsExactly("Air — Moon Safari (a loved song's album)", "Bonobo — Migration (on repeat)");
    }
}
