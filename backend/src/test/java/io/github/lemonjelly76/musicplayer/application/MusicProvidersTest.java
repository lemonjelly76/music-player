package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void cannotSearchWhenNoServiceIsSetUp() {
        MusicProviders none = new MusicProviders(List.of());

        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> none.search(world.withoutService("Craig"), "Pulp", 8))
                .withMessageContaining("No streaming service is set up");
    }
}
