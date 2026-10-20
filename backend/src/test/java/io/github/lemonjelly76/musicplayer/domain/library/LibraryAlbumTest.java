package io.github.lemonjelly76.musicplayer.domain.library;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryAlbumTest {

    private static final PersonId CRAIG = PersonId.random();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private static LibraryAlbum album(String artist, String title, Integer year, Integer rating, String... tags) {
        return LibraryAlbum.create(CRAIG, new AlbumRef(artist, title, Optional.ofNullable(year), ProviderIds.NONE),
                Optional.ofNullable(rating).map(Rating::new), "", List.of(tags), TODAY);
    }

    @Test
    void keepsTagsLowerCaseSortedAndWithoutBlanksOrDuplicates() {
        LibraryAlbum entry = album("Pulp", "His 'n' Hers", 1994, 4, "Britpop", " britpop", "", "Indie");

        assertThat(entry.tags()).containsExactly("britpop", "indie");
        assertThat(entry.hasTag("BRITPOP ")).isTrue();
    }

    @Test
    void filtersByTagRatingDecadeAndArtist() {
        LibraryAlbum pulp = album("Pulp", "Different Class", 1995, 5, "britpop");
        LibraryAlbum unrated = album("Pulp", "This Is Hardcore", 1998, null, "britpop");
        LibraryAlbum davis = album("Miles Davis", "Kind of Blue", 1959, 5, "jazz");

        assertThat(new LibraryFilter(Optional.of("Britpop"), Optional.empty(), Optional.empty(), Optional.empty())
                .matches(davis)).isFalse();
        assertThat(new LibraryFilter(Optional.empty(), Optional.of(4), Optional.empty(), Optional.empty())
                .matches(unrated)).isFalse();
        assertThat(new LibraryFilter(Optional.empty(), Optional.empty(), Optional.of(1990), Optional.of("pulp"))
                .matches(pulp)).isTrue();
        assertThat(new LibraryFilter(Optional.empty(), Optional.empty(), Optional.of(1990), Optional.empty())
                .matches(davis)).isFalse();
        assertThat(LibraryFilter.ALL.matches(unrated)).isTrue();
    }
}
