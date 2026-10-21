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
}
