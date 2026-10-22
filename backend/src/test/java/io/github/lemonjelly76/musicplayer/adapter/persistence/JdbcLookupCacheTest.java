package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.IntegrationTestConfiguration;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.LookupCache;
import io.github.lemonjelly76.musicplayer.domain.port.LookupCache.Lookup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@Transactional
class JdbcLookupCacheTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");

    @Autowired
    private LookupCache cache;

    @Test
    void remembersEachServicesSongs() {
        Lookup<Track> found = new Lookup<>(Optional.of(new Track(Provider.SPOTIFY, "t1", "Tate McRae", "Sports car",
                "So Close To What", Optional.of("https://i/1"))), NOW);
        cache.saveTrack(Provider.SPOTIFY, "tate mcrae|sports car", new Lookup<>(Optional.empty(), NOW.minusSeconds(60)));
        cache.saveTrack(Provider.SPOTIFY, "tate mcrae|sports car", found);
        cache.saveTrack(Provider.SPOTIFY, "made up|never was", new Lookup<>(Optional.empty(), NOW));

        assertThat(cache.track(Provider.SPOTIFY, "tate mcrae|sports car")).contains(found);
        assertThat(cache.track(Provider.SPOTIFY, "made up|never was")).contains(new Lookup<>(Optional.empty(), NOW));
        assertThat(cache.track(Provider.TIDAL, "tate mcrae|sports car")).isEmpty();
        assertThat(cache.album(Provider.SPOTIFY, "tate mcrae|sports car")).isEmpty();
    }

    @Test
    void remembersEachServicesAlbums() {
        Lookup<AlbumRef> found = new Lookup<>(Optional.of(new AlbumRef("Fontaines D.C.", "Romance", Optional.of(2024),
                ProviderIds.of(Provider.TIDAL, "a1"), Optional.of("https://i/a1"))), NOW);
        cache.saveAlbum(Provider.TIDAL, "fontaines d c|romance", found);
        cache.saveAlbum(Provider.TIDAL, "local band|demo tape", new Lookup<>(Optional.empty(), NOW));

        assertThat(cache.album(Provider.TIDAL, "fontaines d c|romance")).contains(found);
        assertThat(cache.album(Provider.TIDAL, "local band|demo tape")).contains(new Lookup<>(Optional.empty(), NOW));
    }
}
