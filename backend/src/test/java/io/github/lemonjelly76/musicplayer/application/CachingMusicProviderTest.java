package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CachingMusicProviderTest {

    private final FakePorts.FakeProvider spotify = new FakePorts.FakeProvider(Provider.SPOTIFY);
    private final FakePorts.Lookups lookups = new FakePorts.Lookups();
    private final MovableClock clock = new MovableClock(Instant.parse("2026-10-22T09:00:00Z"));
    private final CachingMusicProvider cached = new CachingMusicProvider(spotify, lookups, clock);

    @Test
    void asksTheServiceForASongOnlyOnceHoweverItIsWritten() {
        Track song = spotify.carryTrack("t1", "Tate McRae", "Sports car", "So Close To What");

        assertThat(cached.findTrack("Tate McRae", "Sports car")).contains(song);
        assertThat(cached.findTrack("tate mcrae", "Sports Car")).contains(song);

        assertThat(spotify.trackFinds).isEqualTo(1);
    }

    @Test
    void remembersThatTheServiceHasNoSuchSongForAWeek() {
        cached.findTrack("Made Up", "Never Was");
        clock.move(Duration.ofDays(6));
        Track added = spotify.carryTrack("t2", "Made Up", "Never Was", "Debut");

        assertThat(cached.findTrack("Made Up", "Never Was")).isEmpty();
        clock.move(Duration.ofDays(2));
        assertThat(cached.findTrack("Made Up", "Never Was")).contains(added);
        assertThat(spotify.trackFinds).isEqualTo(2);
    }

    @Test
    void asksAgainForASongFoundOverAMonthAgo() {
        spotify.carryTrack("t1", "Tate McRae", "Sports car", "So Close To What");
        cached.findTrack("Tate McRae", "Sports car");
        clock.move(Duration.ofDays(31));

        cached.findTrack("Tate McRae", "Sports car");

        assertThat(spotify.trackFinds).isEqualTo(2);
    }

    @Test
    void remembersNothingWhenTheServiceFails() {
        spotify.failing = true;
        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> cached.findTrack("Tate McRae", "Sports car"));
        spotify.failing = false;
        Track song = spotify.carryTrack("t1", "Tate McRae", "Sports car", "So Close To What");

        assertThat(cached.findTrack("Tate McRae", "Sports car")).contains(song);
    }

    @Test
    void asksTheServiceForAnAlbumOnlyOnce() {
        AlbumRef onSpotify = spotify.carry("a1", album("Fontaines D.C.", "Romance"));

        assertThat(cached.findAlbum("Fontaines D.C.", "Romance")).contains(onSpotify);
        assertThat(cached.findAlbum("fontaines d.c.", "romance")).contains(onSpotify);
        assertThat(cached.findAlbum("Local Band", "Demo Tape")).isEmpty();
        assertThat(cached.findAlbum("Local Band", "Demo Tape")).isEmpty();

        assertThat(spotify.findCalls).isEqualTo(2);
    }

    @Test
    void passesEverythingElseStraightThrough() {
        spotify.carryTrack("t1", "Tate McRae", "Sports car", "So Close To What");

        assertThat(cached.name()).isEqualTo(Provider.SPOTIFY);
        assertThat(cached.searchTracks("sports", 5)).hasSize(1);
        assertThat(cached.trackLink("t1")).isEqualTo(spotify.trackLink("t1"));
        assertThat(cached.connected(PersonId.random())).isFalse();
    }
}
