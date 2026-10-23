package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.ChartService;
import io.github.lemonjelly76.musicplayer.application.LibraryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.SongService;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartPlaylist;
import io.github.lemonjelly76.musicplayer.domain.chart.ResolvedChart;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebMvcTest(ChartController.class)
@Import({SecurityConfiguration.class, ApiErrors.class})
@WithMockUser
class ChartControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-21T09:00:00Z");
    private static final Person CRAIG = Person.create("Craig", Optional.of(Provider.SPOTIFY));
    private static final Track HOUSEWIFE = new Track(Provider.SPOTIFY, "t1", "Tate McRae", "Housewife", "Housewife",
            Optional.of("https://i/1"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PersonService people;
    @MockitoBean
    private ChartService charts;
    @MockitoBean
    private SongService songs;
    @MockitoBean
    private LibraryService library;
    @MockitoBean
    private MusicProviders providers;

    @BeforeEach
    void setUp() {
        given(people.get(CRAIG.id())).willReturn(CRAIG);
        given(providers.link(any(Track.class))).willReturn(Optional.of(URI.create("https://open.spotify.com/track/t1")));
        given(providers.link(any(Person.class), any(AlbumRef.class))).willReturn(Optional.empty());
        given(providers.link(any(SyncedPlaylist.class))).willReturn(Optional.of(URI.create("https://open.spotify.com/playlist/p9")));
    }

    @Test
    void showsTheWeeksChartWithThePersonsRatingsAndTheirPlaylist() {
        given(charts.chartFor(CRAIG)).willReturn(Optional.of(new ResolvedChart(Provider.SPOTIFY, "gb", NOW,
                List.of(new ResolvedChart.Song(1, HOUSEWIFE)),
                List.of(new ResolvedChart.Album(2, new AlbumRef("Fontaines D.C.", "Dopamine Chamber", Optional.empty(),
                        ProviderIds.of(Provider.SPOTIFY, "a1"), Optional.empty()))))));
        given(songs.bySong(CRAIG.id())).willReturn(Map.of(HOUSEWIFE.songKey(), LibrarySong.create(CRAIG.id(), HOUSEWIFE,
                ActivityRating.mainstream(Rating.LOVE, NOW), LocalDate.of(2026, 10, 21))));
        given(library.byKey(CRAIG.id())).willReturn(Map.of());
        given(charts.playlistFor(CRAIG)).willReturn(ChartPlaylist.wantedBy(CRAIG.id())
                .writtenTo(new SyncedPlaylist(Provider.SPOTIFY, "p9"), NOW));

        assertThat(mvc.get().uri("/api/people/{id}/charts", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"country":"gb","updatedAt":"2026-10-21T09:00:00Z","playlistWanted":true,
                         "playlistLink":"https://open.spotify.com/playlist/p9",
                         "songs":[{"position":1,"song":{"ref":"spotify:t1","title":"Housewife","rating":3}}],
                         "albums":[{"position":2,"album":{"album":{"title":"Dopamine Chamber"},"rating":null}}]}""");
    }

    @Test
    void anEmptyChartBeforeTheFirstIsFetched() {
        given(charts.chartFor(CRAIG)).willReturn(Optional.empty());
        given(charts.playlistFor(CRAIG)).willReturn(ChartPlaylist.wantedBy(CRAIG.id()));

        assertThat(mvc.get().uri("/api/people/{id}/charts", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"updatedAt":null,"playlistLink":null,"songs":[],"albums":[]}""");
    }

    @Test
    void turnsTheWeeklyPlaylistOff() {
        given(charts.setPlaylistWanted(CRAIG, false)).willReturn(ChartPlaylist.wantedBy(CRAIG.id()).wanted(false));

        assertThat(mvc.put().uri("/api/people/{id}/chart-playlist", CRAIG.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enabled":false}"""))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"enabled":false}""");
        verify(charts).setPlaylistWanted(CRAIG, false);
    }
}
