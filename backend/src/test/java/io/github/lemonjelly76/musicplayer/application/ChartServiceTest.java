package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.chart.Chart;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartEntry;
import io.github.lemonjelly76.musicplayer.domain.chart.ResolvedChart;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ChartServiceTest {

    /** A Wednesday. */
    private static final Instant WEDNESDAY = Instant.parse("2026-10-21T09:00:00Z");

    private final World world = new World();
    private final FakePorts.Charts repository = new FakePorts.Charts();
    private final AtomicInteger fetches = new AtomicInteger();
    private final MovableClock clock = new MovableClock(WEDNESDAY);
    private final ChartService charts = new ChartService(() -> {
        fetches.incrementAndGet();
        return new Chart("gb", clock.instant(), List.of(
                new ChartEntry(1, "Tate McRae", "Housewife"),
                new ChartEntry(2, "Made Up", "Never Was"),
                new ChartEntry(3, "Jamie Miller", "Babydoll (feat. Someone)")), List.of(
                new ChartEntry(1, "Fontaines D.C.", "Dopamine Chamber"),
                new ChartEntry(2, "Local Band", "Demo Tape")));
    }, repository, world.providers, world.peopleRepository, clock, Duration.ZERO);

    @BeforeEach
    void setUp() {
        world.tidal.carryTrack("t1", "Tate McRae", "Housewife", "Housewife");
        world.tidal.carryTrack("t3", "Jamie Miller, Someone", "Babydoll (feat. Someone)", "Babydoll");
        world.tidal.carry("a1", album("Fontaines D.C.", "Dopamine Chamber"));
    }

    @Test
    void findsTheChartOnEachServiceKeepingWhatCanBePlayed() {
        charts.refreshIfStale();

        ResolvedChart chart = charts.chartFor(world.tidalUser("Craig")).orElseThrow();
        assertThat(chart.songs()).extracting(song -> song.position(), song -> song.track().id())
                .containsExactly(tuple(1, "t1"), tuple(3, "t3"));
        assertThat(chart.albums()).extracting(found -> found.album().title())
                .containsExactly("Dopamine Chamber", "Demo Tape");
        assertThat(chart.albums().getFirst().album().ids().tidal()).contains("a1");
        assertThat(chart.albums().getLast().album().ids().tidal()).isEmpty();
    }

    @Test
    void fetchesTheChartAgainOnlyOnceItIsADayOld() {
        charts.refreshIfStale();
        clock.move(Duration.ofHours(12));
        charts.refreshIfStale();
        clock.move(Duration.ofHours(13));
        charts.refreshIfStale();

        assertThat(fetches).hasValue(2);
    }

    @Test
    void writesEachPersonsPlaylistOnceAWeekReplacingLastWeeks() {
        Person craig = world.tidalUser("Craig");
        charts.refreshIfStale();

        charts.writeDuePlaylists();
        charts.writeDuePlaylists();
        clock.move(Duration.ofDays(5));
        charts.writeDuePlaylists();

        assertThat(world.tidal.playlistCalls).containsExactly(
                "AI Picks — Popular this week [t1, t3]", "AI Picks — Popular this week [t1, t3]");
        assertThat(world.tidal.deletedPlaylists).containsExactly("playlist-1");
        assertThat(charts.playlistFor(craig).synced()).isPresent();
    }

    @Test
    void leavesOutPeopleWhoTurnedItOffOrHaveNoAccount() {
        Person craig = world.tidalUser("Craig");
        world.withoutService("Robin");
        charts.refreshIfStale();

        charts.setPlaylistWanted(craig, false);
        charts.writeDuePlaylists();

        assertThat(world.tidal.playlistCalls).isEmpty();
        assertThat(charts.playlistFor(craig).wanted()).isFalse();
    }
}
