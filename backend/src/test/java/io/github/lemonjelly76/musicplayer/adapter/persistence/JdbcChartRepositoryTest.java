package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.IntegrationTestConfiguration;
import io.github.lemonjelly76.musicplayer.domain.album.AudioFormat;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartPlaylist;
import io.github.lemonjelly76.musicplayer.domain.chart.ResolvedChart;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.ChartRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@Transactional
class JdbcChartRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-21T09:00:00Z");

    @Autowired
    private ChartRepository charts;
    @Autowired
    private PersonRepository people;

    private static ResolvedChart chart(Instant at, String firstSong) {
        return new ResolvedChart(Provider.SPOTIFY, "gb", at,
                List.of(new ResolvedChart.Song(1, new Track(Provider.SPOTIFY, "t1", "Tate McRae", firstSong, "Housewife",
                        Optional.of("https://i/1"), Set.of(AudioFormat.LOSSLESS))), new ResolvedChart.Song(3, new Track(Provider.SPOTIFY, "t3",
                        "Jamie Miller", "Babydoll", "Babydoll", Optional.empty()))),
                List.of(new ResolvedChart.Album(1, new AlbumRef("Fontaines D.C.", "Dopamine Chamber", Optional.empty(),
                        ProviderIds.of(Provider.SPOTIFY, "a1"), Optional.of("https://i/a1"),
                        Set.of(AudioFormat.HI_RES))),
                        new ResolvedChart.Album(2, new AlbumRef("Local Band", "Demo Tape", Optional.empty(),
                                ProviderIds.NONE, Optional.empty()))));
    }

    @Test
    void keepsTheLatestChartForEachService() {
        charts.save(chart(NOW.minusSeconds(86_400), "Old"));
        charts.save(chart(NOW, "Housewife"));

        assertThat(charts.find(Provider.SPOTIFY)).contains(chart(NOW, "Housewife"));
        assertThat(charts.find(Provider.TIDAL)).isEmpty();
    }

    @Test
    void remembersEachPersonsChartPlaylist() {
        Person craig = Person.create("Craig", Optional.of(Provider.SPOTIFY));
        people.save(craig);

        assertThat(charts.playlistFor(craig.id())).isEqualTo(ChartPlaylist.wantedBy(craig.id()));
        ChartPlaylist written = ChartPlaylist.wantedBy(craig.id())
                .writtenTo(new SyncedPlaylist(Provider.SPOTIFY, "pl-1"), NOW);
        charts.save(written);
        charts.save(written.wanted(false));

        assertThat(charts.playlistFor(craig.id())).isEqualTo(written.wanted(false));
    }
}
