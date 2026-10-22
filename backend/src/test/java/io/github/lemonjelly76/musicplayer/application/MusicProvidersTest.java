package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MusicProvidersTest {

    private final World world = new World();

    @Test
    void searchesTheCatalogueEvenForSomeoneWithoutAServiceOfTheirOwn() {
        Person craig = world.withoutService("Craig");
        world.tidal.carry("1", album("Talk Talk", "Spirit of Eden"));
        world.tidal.carry("2", album("Talk Talk", "Laughing Stock"));
        world.tidal.carry("3", album("Pulp", "Different Class"));

        assertThat(world.providers.search(craig, "talk talk", 8))
                .extracting(AlbumRef::title).containsExactly("Spirit of Eden", "Laughing Stock");
    }

    @Test
    void opensAnAlbumOnTheOtherServicesByIdWhenKnownElseBySearch() {
        FakePorts.FakeProvider spotify = new FakePorts.FakeProvider(Provider.SPOTIFY);
        MusicProviders both = new MusicProviders(List.of(world.tidal, spotify));
        Person craig = world.tidalUser("Craig");
        AlbumRef known = new AlbumRef("Air", "Moon Safari", Optional.empty(),
                new ProviderIds(Optional.of("t1"), Optional.of("s1"), Optional.empty()));

        assertThat(both.elsewhere(craig, known))
                .containsExactly(new ServiceLink(Provider.SPOTIFY, URI.create("https://spotify.example/album/s1")));
        assertThat(both.elsewhere(craig, album("Air", "Talkie Walkie")))
                .containsExactly(new ServiceLink(Provider.SPOTIFY, URI.create("https://spotify.example/search/Air+Talkie+Walkie")));
        assertThat(both.elsewhere(world.withoutService("Robin"), known)).extracting(ServiceLink::service)
                .containsExactly(Provider.TIDAL, Provider.SPOTIFY);
    }

    @Test
    void opensASongOnTheServicesOtherThanItsOwnBySearch() {
        FakePorts.FakeProvider spotify = new FakePorts.FakeProvider(Provider.SPOTIFY);
        MusicProviders both = new MusicProviders(List.of(world.tidal, spotify));
        Track song = new Track(Provider.TIDAL, "tr-1", "Air", "Sexy Boy", "Moon Safari", Optional.empty());

        assertThat(both.elsewhere(song))
                .containsExactly(new ServiceLink(Provider.SPOTIFY, URI.create("https://spotify.example/search/Air+Sexy+Boy")));
    }

    @Test
    void cannotSearchWhenNoServiceIsSetUp() {
        MusicProviders none = new MusicProviders(List.of());

        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> none.search(world.withoutService("Craig"), "Pulp", 8))
                .withMessageContaining("No streaming service is set up");
    }
}
