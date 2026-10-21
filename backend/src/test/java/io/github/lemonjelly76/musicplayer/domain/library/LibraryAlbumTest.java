package io.github.lemonjelly76.musicplayer.domain.library;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LibraryAlbumTest {

    private static final PersonId CRAIG = PersonId.random();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");
    private static final Optional<CategoryId> GENERAL = Optional.empty();
    private static final Optional<CategoryId> RUNNING = Optional.of(CategoryId.random());
    private static final Optional<CategoryId> DINNER = Optional.of(CategoryId.random());

    private static LibraryAlbum album(String artist, String title, Integer year, String... tags) {
        return LibraryAlbum.create(CRAIG, new AlbumRef(artist, title, Optional.ofNullable(year), ProviderIds.NONE),
                ActivityRating.general(Rating.LOVE, NOW), "", List.of(tags), TODAY);
    }

    private static ActivityRating rating(Optional<CategoryId> activity, Rating rating) {
        return new ActivityRating(activity, rating, "", NOW);
    }

    @Test
    void keepsTagsLowerCaseSortedAndWithoutBlanksOrDuplicates() {
        LibraryAlbum entry = album("Pulp", "His 'n' Hers", 1994, "Britpop", " britpop", "", "Indie");

        assertThat(entry.tags()).containsExactly("britpop", "indie");
        assertThat(entry.hasTag("BRITPOP ")).isTrue();
    }

    @Test
    void filtersByTagDecadeAndArtist() {
        LibraryAlbum pulp = album("Pulp", "Different Class", 1995, "britpop");
        LibraryAlbum davis = album("Miles Davis", "Kind of Blue", 1959, "jazz");

        assertThat(new LibraryFilter(Optional.of("Britpop"), Optional.empty(), Optional.empty()).matches(davis))
                .isFalse();
        assertThat(new LibraryFilter(Optional.empty(), Optional.of(1990), Optional.of("pulp")).matches(pulp)).isTrue();
        assertThat(new LibraryFilter(Optional.empty(), Optional.of(1990), Optional.empty()).matches(davis)).isFalse();
        assertThat(LibraryFilter.ALL.matches(davis)).isTrue();
    }

    @Test
    void anActivityHasOnlyItsOwnRatingWithGeneralsOnHandAsAHint() {
        LibraryAlbum entry = album("Air", "Moon Safari", 1998).rated(rating(RUNNING, Rating.DISLIKE));

        assertThat(entry.own(DINNER)).isEmpty();
        assertThat(entry.ownOrGeneral(DINNER)).map(ActivityRating::rating).contains(Rating.LOVE);
        assertThat(entry.ownOrGeneral(RUNNING)).map(ActivityRating::rating).contains(Rating.DISLIKE);
    }

    @Test
    void anAlbumIsKeptOnlyWhereItIsRatedLoveOrOk() {
        LibraryAlbum entry = album("Air", "Moon Safari", 1998)
                .rated(rating(RUNNING, Rating.DISLIKE))
                .rated(rating(DINNER, Rating.OK));

        assertThat(entry.keptFor(GENERAL)).isTrue();
        assertThat(entry.keptFor(DINNER)).isTrue();
        assertThat(entry.keptFor(RUNNING)).isFalse();
        assertThat(entry.keptFor(Optional.of(CategoryId.random()))).isFalse();
    }

    @Test
    void notForMeInGeneralKeepsItAwayFromActivitiesWithoutTheirOwnRating() {
        LibraryAlbum entry = album("Air", "Moon Safari", 1998)
                .rated(rating(GENERAL, Rating.DISLIKE))
                .rated(rating(DINNER, Rating.LOVE));

        assertThat(entry.avoidedFor(RUNNING)).isTrue();
        assertThat(entry.knownFor(RUNNING)).isTrue();
        assertThat(entry.avoidedFor(DINNER)).isFalse();
    }

    @Test
    void anAlbumDislikedForOneActivityCanStillBeNewsForAnother() {
        LibraryAlbum disliked = album("Air", "Moon Safari", 1998)
                .unrated(GENERAL)
                .rated(rating(DINNER, Rating.DISLIKE));

        assertThat(disliked.knownFor(DINNER)).isTrue();
        assertThat(disliked.knownFor(RUNNING)).isFalse();
    }

    @Test
    void ratingAgainForAnActivityReplacesTheOldRating() {
        LibraryAlbum entry = album("Air", "Moon Safari", 1998).rated(rating(GENERAL, Rating.OK));

        assertThat(entry.ratings()).singleElement().extracting(ActivityRating::rating).isEqualTo(Rating.OK);
        assertThatIllegalArgumentException().isThrownBy(() -> new LibraryAlbum(entry.id(), CRAIG, entry.album(),
                List.of(rating(RUNNING, Rating.OK), rating(RUNNING, Rating.LOVE)), "", entry.tags(), TODAY));
    }
}
