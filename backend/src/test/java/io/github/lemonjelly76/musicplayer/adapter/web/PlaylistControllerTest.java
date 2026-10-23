package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlaylistCopies;
import io.github.lemonjelly76.musicplayer.application.PlaylistService;
import io.github.lemonjelly76.musicplayer.application.ServiceLink;
import io.github.lemonjelly76.musicplayer.application.SongService;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.AudioFormat;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistCopy;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
    @MockitoBean
    private PlaylistCopies copies;

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
                .writtenTo(2, new SyncedPlaylist(Provider.SPOTIFY, syncedTo));
    }

    @Test
    void listsTheActivitysPlaylistsWithTheirSongsAndThoseInTheAccountToPlay() {
        given(songs.bySong(CRAIG.id())).willReturn(Map.of(SEXY_BOY.songKey(), LibrarySong.create(CRAIG.id(), SEXY_BOY,
                new ActivityRating(Optional.of(RUNNING.id()), Rating.LOVE, "", NOW), LocalDate.of(2026, 10, 21))));
        given(playlists.latest(CRAIG, Optional.of(RUNNING.id()), 5)).willReturn(List.of(
                playlist(NOW, "pl-2"), playlist(NOW.minusSeconds(86_400), "pl-1").outOfAccount()));

        assertThat(mvc.get().uri("/api/people/{id}/playlists?category={c}", CRAIG.id(), RUNNING.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"name":"AI Picks — Running","description":"Bright and fast", "slot":2,
                          "link":"https://open.spotify.com/playlist/pl-2",
                          "tracks":[{"ref":"spotify:tr-1","artist":"Air","title":"Sexy Boy","album":"Moon Safari",
                            "cover":"https://i/300","link":"https://open.spotify.com/track/tr-1","rating":3,
                            "general":false}]},
                         {"link":null, "slot":null}]""");
    }

    @Test
    void putsAPlaylistInASlotInThePersonsAccount() {
        Playlist chosen = playlist(NOW, "pl-3");
        given(playlists.putInSlot(CRAIG, chosen.id(), 2)).willReturn(chosen);
        given(songs.bySong(CRAIG.id())).willReturn(Map.of());

        assertThat(mvc.put().uri("/api/people/{id}/playlists/{playlist}/slot", CRAIG.id(), chosen.id())
                .contentType(MediaType.APPLICATION_JSON).content("{\"slot\":2}"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"slot":2, "link":"https://open.spotify.com/playlist/pl-3"}""");
    }

    @Test
    void aPlaylistOnAServiceTheyHaveSinceLeftIsNotShownAsInTheirAccount() {
        given(songs.bySong(CRAIG.id())).willReturn(Map.of());
        given(playlists.latest(CRAIG, Optional.of(RUNNING.id()), 5)).willReturn(List.of(
                Playlist.create(CRAIG.id(), Optional.of(RUNNING), "", List.of(SEXY_BOY), NOW)
                        .writtenTo(1, new SyncedPlaylist(Provider.TIDAL, "tidal-1"))));

        assertThat(mvc.get().uri("/api/people/{id}/playlists?category={c}", CRAIG.id(), RUNNING.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"slot":null, "link":null}]""");
    }

    @Test
    void showsTheFormatsEachSongCanBePlayedInAndWhereElseItOpens() {
        Track atmos = new Track(Provider.TIDAL, "tr-9", "Air", "La femme d'argent", "Moon Safari", Optional.empty(),
                Set.of(AudioFormat.DOLBY_ATMOS));
        given(songs.bySong(CRAIG.id())).willReturn(Map.of());
        given(providers.elsewhere(atmos)).willReturn(List.of(
                new ServiceLink(Provider.SPOTIFY, URI.create("https://open.spotify.com/search/Air"))));
        given(playlists.latest(CRAIG, Optional.of(RUNNING.id()), 5)).willReturn(List.of(
                Playlist.create(CRAIG.id(), Optional.of(RUNNING), "", List.of(atmos), NOW)));

        assertThat(mvc.get().uri("/api/people/{id}/playlists?category={c}", CRAIG.id(), RUNNING.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"tracks":[{"ref":"tidal:tr-9","formats":["DOLBY_ATMOS"],
                          "elsewhere":[{"service":"spotify","link":"https://open.spotify.com/search/Air"}]}]}]""");
    }

    @Test
    void listsWhereEachActivitysPlaylistsAreCopied() {
        given(copies.of(CRAIG)).willReturn(List.of(PlaylistCopy.wanted(CRAIG.id(), Optional.of(RUNNING.id()),
                Provider.TIDAL).writtenAs("tidal-copy")));
        given(providers.link(new SyncedPlaylist(Provider.TIDAL, "tidal-copy")))
                .willReturn(Optional.of(URI.create("https://tidal.com/browse/playlist/tidal-copy")));

        assertThat(mvc.get().uri("/api/people/{id}/playlist-copies", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isEqualTo("""
                        [{"categoryId":"%s","service":"tidal","link":"https://tidal.com/browse/playlist/tidal-copy"}]"""
                        .formatted(RUNNING.id()));
    }

    @Test
    void startsAndStopsCopyingAnActivitysPlaylists() {
        given(copies.keep(CRAIG, Optional.of(RUNNING), Provider.TIDAL)).willReturn("copied to tidal");

        assertThat(mvc.put().uri("/api/people/{id}/playlist-copy?category={c}", CRAIG.id(), RUNNING.id())
                .contentType(MediaType.APPLICATION_JSON).content("{\"service\":\"tidal\"}"))
                .hasStatusOk().bodyJson().isEqualTo("""
                        {"status":"copied to tidal"}""");
        assertThat(mvc.put().uri("/api/people/{id}/playlist-copy?category={c}", CRAIG.id(), RUNNING.id())
                .contentType(MediaType.APPLICATION_JSON).content("{\"service\":null}"))
                .hasStatusOk();
        then(copies).should().stop(CRAIG, Optional.of(RUNNING));
    }

    @Test
    void aSlotMustBeGiven() {
        assertThat(mvc.put().uri("/api/people/{id}/playlists/{playlist}/slot", CRAIG.id(), PlaylistId.random())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
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

    @Test
    void deletesAPlaylistAndSaysWhereFrom() {
        PlaylistId id = PlaylistId.random();
        given(playlists.delete(CRAIG, id)).willReturn("Deleted AI Picks — Running 1 here and from spotify");

        assertThat(mvc.delete().uri("/api/people/{id}/playlists/{playlist}", CRAIG.id(), id))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"status":"Deleted AI Picks — Running 1 here and from spotify"}""");
    }
}
