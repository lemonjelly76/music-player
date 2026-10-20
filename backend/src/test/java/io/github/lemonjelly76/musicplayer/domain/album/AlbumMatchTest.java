package io.github.lemonjelly76.musicplayer.domain.album;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlbumMatchTest {

    private static final AlbumRef DELUXE = AlbumRef.of("Radiohead", "OK Computer OKNOTOK 1997 2017");
    private static final AlbumRef ORIGINAL = AlbumRef.of("Radiohead", "OK Computer (Remastered)");
    private static final AlbumRef TRIBUTE = AlbumRef.of("Various Artists", "OK Computer");

    @Test
    void prefersAnExactMatch() {
        assertThat(AlbumMatch.best(List.of(TRIBUTE, DELUXE, ORIGINAL), "Radiohead", "OK Computer")).contains(ORIGINAL);
    }

    @Test
    void fallsBackToALongerTitleByTheSameArtist() {
        assertThat(AlbumMatch.best(List.of(TRIBUTE, DELUXE), "Radiohead", "OK Computer")).contains(DELUXE);
    }

    @Test
    void neverMatchesAnotherArtistOrAPartialWord() {
        assertThat(AlbumMatch.best(List.of(TRIBUTE), "Radiohead", "OK Computer")).isEmpty();
        assertThat(AlbumMatch.best(List.of(AlbumRef.of("Radiohead", "OK Computers")), "Radiohead", "OK Computer"))
                .isEmpty();
    }
}
