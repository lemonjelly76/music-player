package io.github.lemonjelly76.musicplayer.domain.plays;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListeningStatsTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    private static AlbumPlays plays(String artist, String title, int count) {
        return new AlbumPlays(AlbumRef.of(artist, title), count, NOW);
    }

    @Test
    void addsUpTracksAndAlbumsAndKeepsTheMostPlayed() {
        ListeningStats stats = ListeningStats.of(List.of(
                plays("Air", "Moon Safari", 9),
                plays("Pulp", "Different Class", 4),
                plays("Talk Talk", "Spirit of Eden", 2)), 2);

        assertThat(stats.tracks()).isEqualTo(15);
        assertThat(stats.albums()).isEqualTo(3);
        assertThat(stats.topAlbums()).extracting(total -> total.album().title())
                .containsExactly("Moon Safari", "Different Class");
    }

    @Test
    void artistsAddUpAcrossTheirAlbumsWhateverTheCase() {
        ListeningStats stats = ListeningStats.of(List.of(
                plays("Air", "Moon Safari", 5),
                plays("Pulp", "Different Class", 6),
                plays("AIR", "Talkie Walkie", 3)), 10);

        assertThat(stats.topArtists()).containsExactly(
                new ArtistPlays("Air", 8, 2),
                new ArtistPlays("Pulp", 6, 1));
    }

    @Test
    void nothingPlayedIsAllZeroes() {
        ListeningStats stats = ListeningStats.of(List.of(), 5);

        assertThat(stats.tracks()).isZero();
        assertThat(stats.albums()).isZero();
        assertThat(stats.topAlbums()).isEmpty();
        assertThat(stats.topArtists()).isEmpty();
    }
}
