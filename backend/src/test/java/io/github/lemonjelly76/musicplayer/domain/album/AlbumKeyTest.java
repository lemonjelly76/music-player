package io.github.lemonjelly76.musicplayer.domain.album;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlbumKeyTest {

    @Test
    void ignoresCaseLeadingTheEditionsAndPunctuation() {
        assertThat(AlbumKey.of("The Beatles", "Abbey Road (Remastered 2019)"))
                .isEqualTo(AlbumKey.of("Beatles", "abbey road"));
        assertThat(AlbumKey.of("Pulp", "Different Class [Deluxe Edition]"))
                .isEqualTo(AlbumKey.of("PULP", "Different Class"));
    }

    @Test
    void ignoresAccentsAndReadsAmpersandAsAnd() {
        assertThat(AlbumKey.of("Björk", "Homogenic")).isEqualTo(AlbumKey.of("Bjork", "Homogenic"));
        assertThat(AlbumKey.of("Simon & Garfunkel", "Bookends"))
                .isEqualTo(AlbumKey.of("Simon and Garfunkel", "Bookends"));
    }

    @Test
    void keepsBracketsThatAreNotEditions() {
        assertThat(AlbumKey.of("Pink Floyd", "Ummagumma (Live)"))
                .isNotEqualTo(AlbumKey.of("Pink Floyd", "Ummagumma"));
    }

    @Test
    void tellsDifferentArtistsApart() {
        assertThat(AlbumKey.of("Blur", "Blur")).isNotEqualTo(AlbumKey.of("Weezer", "Blur"));
    }
}
