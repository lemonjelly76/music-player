package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlaylistService;
import io.github.lemonjelly76.musicplayer.application.SongService;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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

@WebMvcTest(PlaylistController.class)
@Import({SecurityConfiguration.class, ApiErrors.class})
@WithMockUser
class PlaylistControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-21T18:00:00Z");
    private static final Person CRAIG = Person.create("Craig", Optional.of(Provider.SPOTIFY));
    private static final Category RUNNING = Category.create("Running", "");
    private static final Track SEXY_BOY = new Track(Provider.SPOTIFY, "tr-1", "Air", "Sexy Boy", "Moon Safari",
            Optional.of("https://i/300"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PersonService people;
    @MockitoBean
    private CategoryService categories;
    @MockitoBean
    private PlaylistService playlists;
    @MockitoBean
    private MusicProviders providers;
    @MockitoBean
    private SongService songs;

    @BeforeEach
    void setUp() {
        given(people.get(CRAIG.id())).willReturn(CRAIG);
        given(categories.find(RUNNING.id().toString())).willReturn(RUNNING);
        given(providers.link(any(Track.class))).willAnswer(call ->
                Optional.of(URI.create("https://open.spotify.com/track/" + ((Track) call.getArgument(0)).id())));
        given(providers.link(any(SyncedPlaylist.class))).willAnswer(call ->
                Optional.of(URI.create("https://open.spotify.com/playlist/" + ((SyncedPlaylist) call.getArgument(0)).playlistId())));
    }

    private static Playlist playlist(Instant at, String syncedTo) {
        return Playlist.create(CRAIG.id(), Optional.of(RUNNING), "Bright and fast", List.of(SEXY_BOY), at)
                .syncedTo(new SyncedPlaylist(Provider.SPOTIFY, syncedTo));
    }

    @Test
    void listsTheActivitysPlaylistsWithTheirSongsAndOnlyTheNewestToPlay() {
        given(songs.bySong(CRAIG.id())).willReturn(Map.of(SEXY_BOY.songKey(), LibrarySong.create(CRAIG.id(), SEXY_BOY,
                new ActivityRating(Optional.of(RUNNING.id()), Rating.LOVE, "", NOW), LocalDate.of(2026, 10, 21))));
        given(playlists.latest(CRAIG, Optional.of(RUNNING.id()), 5)).willReturn(List.of(
                playlist(NOW, "pl-2"), playlist(NOW.minusSeconds(86_400), "pl-1")));

        assertThat(mvc.get().uri("/api/people/{id}/playlists?category={c}", CRAIG.id(), RUNNING.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"name":"AI Picks — Running","description":"Bright and fast",
                          "link":"https://open.spotify.com/playlist/pl-2",
                          "tracks":[{"ref":"spotify:tr-1","artist":"Air","title":"Sexy Boy","album":"Moon Safari",
                            "cover":"https://i/300","link":"https://open.spotify.com/track/tr-1","rating":3,
                            "general":false}]},
                         {"link":null}]""");
    }

    @Test
    void generalIsItsOwnPlaylist() {
        given(playlists.latest(CRAIG, Optional.empty(), 1)).willReturn(List.of());

        assertThat(mvc.get().uri("/api/people/{id}/playlists?category=none&limit=1", CRAIG.id()))
                .hasStatusOk().bodyJson().isEqualTo("[]");
    }

    @Test
    void aLimitOutsideOneToTwentyIsABadRequest() {
        assertThat(mvc.get().uri("/api/people/{id}/playlists?limit=0", CRAIG.id())).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
