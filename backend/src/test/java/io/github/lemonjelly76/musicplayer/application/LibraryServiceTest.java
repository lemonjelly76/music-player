package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.LibraryService.Added;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryFilter;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LibraryServiceTest {

    private final World world = new World();

    @Test
    void addsTheAlbumAndSavesItToTheStreamingFavourites() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("77", album("Joni Mitchell", "Blue"));

        Added added = world.library.add(craig, "tidal:77", Optional.of(new Rating(5)), "Perfect", List.of("Folk"));

        assertThat(added.alreadyInLibrary()).isFalse();
        assertThat(added.favouriteProblem()).isEmpty();
        assertThat(added.entry().tags()).containsExactly("folk");
        assertThat(added.entry().addedOn()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(world.tidal.favourites.get(craig.id())).containsExactly("77");
    }

    @Test
    void addingAnAlbumAlreadyThereLeavesItAlone() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("77", album("Joni Mitchell", "Blue"));
        LibraryAlbum first = world.library.add(craig, "tidal:77", Optional.of(new Rating(5)), "", List.of()).entry();

        Added again = world.library.add(craig, "Joni Mitchell — Blue", Optional.of(new Rating(2)), "", List.of());

        assertThat(again.alreadyInLibrary()).isTrue();
        assertThat(again.entry()).isEqualTo(first);
        assertThat(world.library.list(craig.id(), LibraryFilter.ALL)).hasSize(1);
    }

    @Test
    void saysWhyAnAlbumDidNotReachTheFavourites() {
        Person craig = world.tidalUser("Craig");

        Added added = world.library.add(craig, "Local Band — Demo Tape", Optional.empty(), "", List.of());

        assertThat(added.entry().album()).isEqualTo(album("Local Band", "Demo Tape"));
        assertThat(added.favouriteProblem()).isEqualTo("not saved to tidal favourites: it is not on the service");
    }

    @Test
    void updatesOnlyWhatIsGiven() {
        Person craig = world.withoutService("Craig");
        LibraryAlbum entry = world.library.add(craig, "Joni Mitchell — Blue", Optional.of(new Rating(4)), "Sad",
                List.of("folk")).entry();

        LibraryAlbum changed = world.library.update(craig.id(), entry.id(), Optional.of(new Rating(5)),
                Optional.empty(), Optional.of(List.of("folk", "70s")));

        assertThat(changed.rating()).contains(new Rating(5));
        assertThat(changed.notes()).isEqualTo("Sad");
        assertThat(changed.tags()).containsExactly("70s", "folk");
    }

    @Test
    void cannotTouchSomeoneElsesAlbum() {
        Person craig = world.withoutService("Craig");
        Person sam = world.withoutService("Sam");
        LibraryAlbum entry = world.library.add(craig, "Joni Mitchell — Blue", Optional.empty(), "", List.of()).entry();

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> world.library.remove(sam.id(), entry.id()));
    }

    @Test
    void listsTheBestRatedFirst() {
        Person craig = world.withoutService("Craig");
        world.library.add(craig, "Blur — 13", Optional.of(new Rating(3)), "", List.of());
        world.library.add(craig, "Pulp — Different Class", Optional.of(new Rating(5)), "", List.of());
        world.library.add(craig, "Air — Moon Safari", Optional.empty(), "", List.of());

        assertThat(world.library.list(craig.id(), LibraryFilter.ALL))
                .extracting(entry -> entry.album().artist()).containsExactly("Pulp", "Blur", "Air");
    }

    @Test
    void importsFavouritesItDoesNotHaveYet() {
        Person craig = world.tidalUser("Craig");
        world.library.add(craig, "Joni Mitchell — Blue", Optional.of(new Rating(5)), "", List.of());
        AlbumRef blue = world.tidal.carry("77", album("Joni Mitchell", "Blue"));
        AlbumRef hejira = world.tidal.carry("78", album("Joni Mitchell", "Hejira"));
        world.tidal.storedFavourites.put(craig.id(), List.of(blue, hejira));

        LibraryService.Imported imported = world.library.importFavourites(craig);

        assertThat(imported).isEqualTo(new LibraryService.Imported("tidal", 1, 1));
        assertThat(world.library.find(craig.id(), hejira.key()).orElseThrow().tags()).containsExactly("imported");
    }

    @Test
    void cannotImportWithoutAService() {
        Person sam = world.withoutService("Sam");

        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> world.library.importFavourites(sam));
    }
}
