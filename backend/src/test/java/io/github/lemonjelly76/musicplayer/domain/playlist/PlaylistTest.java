package io.github.lemonjelly76.musicplayer.domain.playlist;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PlaylistTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    private static List<Track> tracks(int count) {
        return IntStream.range(0, count)
                .mapToObj(n -> new Track(Provider.SPOTIFY, "t" + n, "Artist", "Song " + n, "Album", Optional.empty()))
                .toList();
    }

    @Test
    void isNamedForItsActivitySoItReplacesTheSamePlaylist() {
        Playlist running = Playlist.create(PersonId.random(), Optional.of(Category.create("Running", "")),
                "Fast and bright", tracks(3), NOW);
        Playlist general = Playlist.create(PersonId.random(), Optional.empty(), "", tracks(3), NOW);

        assertThat(running.name()).isEqualTo("AI Picks — Running");
        assertThat(general.name()).isEqualTo("AI Picks");
    }

    @Test
    void holdsAtMostFiftyTracks() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                Playlist.create(PersonId.random(), Optional.empty(), "", tracks(Playlist.SIZE + 1), NOW));
    }

    @Test
    void aSongIsTheSameWhateverItsEditionOrGuests() {
        Track original = new Track(Provider.SPOTIFY, "1", "Air, Beth Hirsch", "All I Need (feat. Beth Hirsch)", "", Optional.empty());
        Track remaster = new Track(Provider.TIDAL, "2", "Air", "All I Need - Remastered 2018", "", Optional.empty());
        Track live = new Track(Provider.TIDAL, "3", "Air", "All I Need - Live", "", Optional.empty());

        assertThat(original.songKey()).isEqualTo(remaster.songKey()).isEqualTo("air|all i need");
        assertThat(live.songKey()).isNotEqualTo(original.songKey());
    }
}
