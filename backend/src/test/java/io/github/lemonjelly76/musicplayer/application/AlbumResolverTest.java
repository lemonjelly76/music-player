package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.AlbumResolver.Resolution;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;

class AlbumResolverTest {

    private final World world = new World();

    @Test
    void readsAServiceId() {
        AlbumRef blue = world.tidal.carry("77", album("Joni Mitchell", "Blue", 1971));

        assertThat(world.resolver.resolve("tidal:77", Optional.empty()).album()).contains(blue);
        assertThat(world.resolver.resolve("TIDAL:404", Optional.empty()).problem()).isEqualTo("No album with id TIDAL:404");
    }

    @Test
    void findsANamedAlbumOnThePersonsService() {
        AlbumRef blue = world.tidal.carry("77", album("Joni Mitchell", "Blue (2021 Remaster)"));

        assertThat(world.resolver.resolve("joni mitchell — Blue", Optional.of(world.tidal)).album()).contains(blue);
        assertThat(world.resolver.resolve("Joni Mitchell - Court and Spark", Optional.of(world.tidal)).problem())
                .isEqualTo("Joni Mitchell — Court and Spark is not on tidal");
    }

    @Test
    void checksANamedAlbumAgainstTheNeutralCatalogueWhenThereIsNoService() {
        AlbumRef blue = world.catalogue.know(new AlbumRef("Joni Mitchell", "Blue", Optional.of(1971),
                ProviderIds.musicBrainz("mb-blue")));

        assertThat(world.resolver.resolve("Joni Mitchell – Blue", Optional.empty()).album()).contains(blue);
        assertThat(world.resolver.resolve("Joni Mitchell – Blew", Optional.empty()).album()).isEmpty();
    }

    @Test
    void anAlbumFoundByMusicBrainzIdMustAlsoBeOnTheService() {
        world.catalogue.know(new AlbumRef("Joni Mitchell", "Blue", Optional.empty(), ProviderIds.musicBrainz("mb-blue")));

        assertThat(world.resolver.resolve("mbid:mb-blue", Optional.of(world.tidal)).problem())
                .isEqualTo("Joni Mitchell — Blue is not on tidal");

        world.tidal.carry("77", album("Joni Mitchell", "Blue"));
        Resolution onTidal = world.resolver.resolve("mbid:mb-blue", Optional.of(world.tidal));
        assertThat(onTidal.album().orElseThrow().ids())
                .isEqualTo(new ProviderIds(Optional.of("77"), Optional.empty(), Optional.of("mb-blue")));
    }

    @Test
    void explainsAReferenceItCannotRead() {
        assertThat(world.resolver.resolve("Blue", Optional.empty()).problem())
                .contains("tidal:251380836", "'Artist — Title'");
    }

    @Test
    void lenientlyKeepsAnAlbumNoCatalogueKnowsByName() {
        assertThat(world.resolver.resolveLeniently("Local Band — Demo Tape", Optional.of(world.tidal)))
                .contains(album("Local Band", "Demo Tape"));
        assertThat(world.resolver.resolveLeniently("Demo Tape", Optional.empty())).isEmpty();
    }
}
