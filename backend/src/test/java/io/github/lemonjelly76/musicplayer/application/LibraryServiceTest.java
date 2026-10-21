package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.LibraryService.Added;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryFilter;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LibraryServiceTest {

    private static final Optional<Category> GENERAL = Optional.empty();

    private final World world = new World();

    @Test
    void addsALovedAlbumAndSavesItToTheStreamingFavourites() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("77", album("Joni Mitchell", "Blue"));

        Added added = world.library.add(craig, "tidal:77", GENERAL, Rating.LOVE, "Perfect", List.of("Folk"));

        assertThat(added.alreadyInLibrary()).isFalse();
        assertThat(added.favouriteProblem()).isEmpty();
        assertThat(added.entry().tags()).containsExactly("folk");
        assertThat(added.entry().addedOn()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(world.tidal.favourites.get(craig.id())).containsExactly("77");
    }

    @Test
    void addingAnAlbumAlreadyThereKeepsItsNotesAndTakesTheNewRating() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("77", album("Joni Mitchell", "Blue"));
        world.library.add(craig, "tidal:77", GENERAL, Rating.LOVE, "Sad", List.of());

        Added again = world.library.add(craig, "Joni Mitchell — Blue", GENERAL, Rating.OK, "", List.of());

        assertThat(again.alreadyInLibrary()).isTrue();
        assertThat(again.entry().notes()).isEqualTo("Sad");
        assertThat(again.entry().rating(Optional.empty())).map(ActivityRating::rating).contains(Rating.OK);
        assertThat(world.library.all(craig.id(), LibraryFilter.ALL)).hasSize(1);
    }

    @Test
    void saysWhyAnAlbumDidNotReachTheFavourites() {
        Person craig = world.tidalUser("Craig");

        Added added = world.library.add(craig, "Local Band — Demo Tape", GENERAL, Rating.LOVE, "", List.of());

        assertThat(added.entry().album()).isEqualTo(album("Local Band", "Demo Tape"));
        assertThat(added.favouriteProblem()).isEqualTo("not saved to tidal favourites: it is not on the service");
    }

    @Test
    void onlyLovedAlbumsGoToTheStreamingFavourites() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("77", album("Joni Mitchell", "Blue"));

        world.library.add(craig, "tidal:77", GENERAL, Rating.OK, "", List.of());

        assertThat(world.tidal.favourites.get(craig.id())).isNull();
    }

    @Test
    void updatesOnlyWhatIsGiven() {
        Person craig = world.withoutService("Craig");
        LibraryAlbum entry = world.library.add(craig, "Joni Mitchell — Blue", GENERAL, Rating.LOVE, "Sad",
                List.of("folk")).entry();

        LibraryAlbum changed = world.library.update(craig.id(), entry.id(), Optional.empty(),
                Optional.of(List.of("folk", "70s")));

        assertThat(changed.notes()).isEqualTo("Sad");
        assertThat(changed.tags()).containsExactly("70s", "folk");
    }

    @Test
    void cannotTouchSomeoneElsesAlbum() {
        Person craig = world.withoutService("Craig");
        Person sam = world.withoutService("Sam");
        LibraryAlbum entry = world.library.add(craig, "Joni Mitchell — Blue", GENERAL, Rating.LOVE, "", List.of())
                .entry();

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> world.library.remove(sam.id(), entry.id()));
    }

    @Test
    void anActivitysLibraryHasWhatIsLovedOrOkThereLovedFirst() {
        Person craig = world.withoutService("Craig");
        Category running = world.categories.upsert("Running", "");
        world.library.add(craig, "Blur — 13", GENERAL, Rating.OK, "", List.of());
        world.library.add(craig, "Pulp — Different Class", Optional.of(running), Rating.LOVE, "", List.of());
        world.library.add(craig, "Air — Moon Safari", GENERAL, Rating.LOVE, "", List.of());
        world.library.rate(craig, "Air — Moon Safari", Optional.of(running), Optional.of(Rating.DISLIKE), "");

        assertThat(world.library.libraryFor(craig.id(), Optional.of(running.id()), LibraryFilter.ALL))
                .extracting(entry -> entry.album().artist()).containsExactly("Pulp", "Blur");
        assertThat(world.library.libraryFor(craig.id(), Optional.empty(), LibraryFilter.ALL))
                .extracting(entry -> entry.album().artist()).containsExactly("Air", "Blur");
    }

    @Test
    void listsWhatIsNotForThePersonForAnActivityIncludingGeneralDislikesStandingIn() {
        Person craig = world.withoutService("Craig");
        Category running = world.categories.upsert("Running", "");
        world.library.add(craig, "Blur — 13", GENERAL, Rating.DISLIKE, "", List.of());
        world.library.add(craig, "Air — Moon Safari", GENERAL, Rating.LOVE, "", List.of());
        world.library.rate(craig, "Air — Moon Safari", Optional.of(running), Optional.of(Rating.DISLIKE), "");
        world.library.add(craig, "Pulp — His 'n' Hers", GENERAL, Rating.DISLIKE, "", List.of());
        world.library.rate(craig, "Pulp — His 'n' Hers", Optional.of(running), Optional.of(Rating.LOVE), "");

        assertThat(world.library.dislikedFor(craig.id(), Optional.of(running.id())))
                .extracting(entry -> entry.album().artist()).containsExactly("Air", "Blur");
        assertThat(world.library.dislikedFor(craig.id(), Optional.empty()))
                .extracting(entry -> entry.album().artist()).containsExactly("Blur", "Pulp");
    }

    @Test
    void albumsToSortForAnActivityAreKeptElsewhereButNotRatedThereLovedFirst() {
        Person craig = world.withoutService("Craig");
        Category running = world.categories.upsert("Running", "");
        Category dinner = world.categories.upsert("Dinner", "");
        world.library.add(craig, "Blur — 13", GENERAL, Rating.OK, "", List.of());
        world.library.add(craig, "Pulp — Different Class", Optional.of(dinner), Rating.LOVE, "", List.of());
        world.library.add(craig, "Air — Moon Safari", GENERAL, Rating.LOVE, "", List.of());
        world.library.add(craig, "Suede — Suede", GENERAL, Rating.LOVE, "", List.of());
        world.library.rate(craig, "Suede — Suede", Optional.of(running), Optional.of(Rating.OK), "");
        world.library.add(craig, "Oasis — Be Here Now", GENERAL, Rating.DISLIKE, "", List.of());

        assertThat(world.library.toSortFor(craig.id(), Optional.of(running.id())))
                .extracting(entry -> entry.album().artist()).containsExactly("Air", "Blur", "Pulp");
        assertThat(world.library.toSortFor(craig.id(), Optional.empty()))
                .extracting(entry -> entry.album().artist()).containsExactly("Pulp");
    }

    @Test
    void takingAwayAnAlbumsLastRatingTakesItOutOfTheLibrary() {
        Person craig = world.withoutService("Craig");
        world.library.add(craig, "Blur — 13", GENERAL, Rating.OK, "", List.of());

        Optional<LibraryAlbum> left = world.library.rate(craig, "Blur — 13", GENERAL, Optional.empty(), "");

        assertThat(left).isEmpty();
        assertThat(world.library.all(craig.id(), LibraryFilter.ALL)).isEmpty();
    }

    @Test
    void importsFavouritesItDoesNotHaveYet() {
        Person craig = world.tidalUser("Craig");
        world.library.add(craig, "Joni Mitchell — Blue", GENERAL, Rating.LOVE, "", List.of());
        AlbumRef blue = world.tidal.carry("77", album("Joni Mitchell", "Blue"));
        AlbumRef hejira = world.tidal.carry("78", album("Joni Mitchell", "Hejira"));
        world.tidal.storedFavourites.put(craig.id(), List.of(blue, hejira));

        LibraryService.Imported imported = world.library.importFavourites(craig);

        assertThat(imported).isEqualTo(new LibraryService.Imported("tidal", 1, 1));
        assertThat(world.library.find(craig.id(), hejira.key()).orElseThrow().tags()).containsExactly("imported");
    }

    @Test
    void fillsInTheServiceIdsOfAlbumsAddedBeforeTheServiceWasConnected() {
        Person craig = world.tidalUser("Craig");
        LibraryAlbum migration = world.library.add(craig, album("Bonobo", "Migration"), GENERAL, Rating.LOVE, "", List.of())
                .entry();
        world.library.add(craig, album("Local Band", "Demo Tape"), GENERAL, Rating.LOVE, "", List.of());
        world.tidal.carry("t-mig", album("Bonobo", "Migration (Deluxe Edition)"));

        int filled = world.library.fillServiceIds(craig, 10);

        assertThat(filled).isEqualTo(1);
        assertThat(world.libraryRepository.findById(migration.id()).orElseThrow().album().ids().tidal()).contains("t-mig");
        assertThat(world.libraryRepository.findById(migration.id()).orElseThrow().album().title()).isEqualTo("Migration");
    }

    @Test
    void looksUpAtMostTheGivenNumberOfAlbumsAtATime() {
        Person craig = world.tidalUser("Craig");
        for (int i = 0; i < 5; i++) {
            world.library.add(craig, album("Artist " + i, "Album " + i), GENERAL, Rating.LOVE, "", List.of());
        }
        world.tidal.findCalls = 0;

        world.library.fillServiceIds(craig, 2);

        assertThat(world.tidal.findCalls).isEqualTo(2);
    }

    @Test
    void anAlbumWithoutAnIdStillLinksToASearchOnTheService() {
        Person craig = world.tidalUser("Craig");

        assertThat(world.providers.link(craig, album("Bonobo", "Migration")))
                .contains(URI.create("https://tidal.example/search/Bonobo+Migration"));
    }

    @Test
    void cannotImportWithoutAService() {
        Person sam = world.withoutService("Sam");

        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> world.library.importFavourites(sam));
    }
}
