package io.github.lemonjelly76.musicplayer.domain.library;

import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryArtistTest {

    @Test
    void isKnownByItsNameWhateverTheCaseAccentsOrALeadingThe() {
        LibraryArtist artist = LibraryArtist.create(PersonId.random(), "Beyoncé",
                ActivityRating.mainstream(Rating.LOVE, Instant.EPOCH), LocalDate.EPOCH);

        assertThat(artist.isCalled("beyonce")).isTrue();
        assertThat(LibraryArtist.key("The Strokes")).isEqualTo(LibraryArtist.key("strokes"));
    }

    @Test
    void readsEachArtistCreditedOnASongOrAlbum() {
        assertThat(LibraryArtist.credited("Sam Fender & Olivia Dean")).contains("sam fender", "olivia dean",
                "sam fender and olivia dean");
        assertThat(LibraryArtist.credited("Daft Punk feat. Pharrell Williams, Nile Rodgers"))
                .contains("daft punk", "pharrell williams", "nile rodgers");
        assertThat(LibraryArtist.credited("Mumford & Sons")).contains("mumford and sons");
    }
}
