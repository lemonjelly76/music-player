package io.github.lemonjelly76.musicplayer.domain.library;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LibrarySongTest {

    private static final Instant NOW = Instant.parse("2026-10-21T18:00:00Z");
    private static final Optional<CategoryId> RUNNING = Optional.of(CategoryId.random());
    private static final Optional<CategoryId> DINNER = Optional.of(CategoryId.random());
    private static final Track SEXY_BOY = new Track(Provider.SPOTIFY, "t1", "Air", "Sexy Boy", "Moon Safari",
            Optional.empty());

    private static LibrarySong lovedInGeneral() {
        return LibrarySong.create(PersonId.random(), SEXY_BOY, ActivityRating.general(Rating.LOVE, NOW),
                LocalDate.of(2026, 10, 21));
    }

    @Test
    void generalStandsInForAnActivityWithoutItsOwnRating() {
        LibrarySong song = lovedInGeneral().rated(new ActivityRating(RUNNING, Rating.DISLIKE, "", NOW.plusSeconds(1)));

        assertThat(song.rating(RUNNING)).map(ActivityRating::rating).contains(Rating.DISLIKE);
        assertThat(song.rating(DINNER)).map(ActivityRating::rating).contains(Rating.LOVE);
        assertThat(song.own(DINNER)).isEmpty();
        assertThat(song.keptFor(RUNNING)).isFalse();
        assertThat(song.keptFor(DINNER)).isTrue();
    }

    @Test
    void aNewRatingForAnActivityReplacesItsOldOneAndCanBeTakenAway() {
        LibrarySong song = lovedInGeneral()
                .rated(new ActivityRating(RUNNING, Rating.OK, "", NOW))
                .rated(new ActivityRating(RUNNING, Rating.LOVE, "", NOW.plusSeconds(1)));

        assertThat(song.ratings()).hasSize(2);
        assertThat(song.own(RUNNING)).map(ActivityRating::rating).contains(Rating.LOVE);
        assertThat(song.unrated(RUNNING).own(RUNNING)).isEmpty();
        assertThat(song.unrated(RUNNING).unrated(Optional.empty()).ratings()).isEmpty();
    }
}
