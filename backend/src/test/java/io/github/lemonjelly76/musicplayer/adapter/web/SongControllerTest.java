package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.SongService;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebMvcTest(SongController.class)
@Import({SecurityConfiguration.class, ApiErrors.class})
@WithMockUser
class SongControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-21T18:00:00Z");
    private static final Person CRAIG = Person.create("Craig", Optional.of(Provider.SPOTIFY));
    private static final Category RUNNING = Category.create("Running", "");
    private static final Track SEXY_BOY = new Track(Provider.SPOTIFY, "t1", "Air", "Sexy Boy", "Moon Safari",
            Optional.of("https://i/300"));
    private static final Track KELLY = new Track(Provider.SPOTIFY, "t2", "Air", "Kelly Watch the Stars", "Moon Safari",
            Optional.empty());

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PersonService people;
    @MockitoBean
    private CategoryService categories;
    @MockitoBean
    private SongService songs;
    @MockitoBean
    private MusicProviders providers;

    @BeforeEach
    void setUp() {
        given(people.get(CRAIG.id())).willReturn(CRAIG);
        given(categories.find(RUNNING.id().toString())).willReturn(RUNNING);
        given(providers.link(any(Track.class))).willAnswer(call ->
                Optional.of(URI.create("https://open.spotify.com/track/" + ((Track) call.getArgument(0)).id())));
    }

    private static LibrarySong lovedInGeneral(Track track) {
        return LibrarySong.create(CRAIG.id(), track, ActivityRating.mainstream(Rating.LOVE, NOW), LocalDate.of(2026, 10, 21));
    }

    @Test
    void listsTheActivitysSongsWithTheirRatingThere() {
        given(songs.songsFor(CRAIG.id(), Optional.of(RUNNING.id()))).willReturn(List.of(
                lovedInGeneral(SEXY_BOY).rated(new ActivityRating(Optional.of(RUNNING.id()), Rating.OK, "", NOW))));

        assertThat(mvc.get().uri("/api/people/{id}/songs?category={c}", CRAIG.id(), RUNNING.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"ref":"spotify:t1","title":"Sexy Boy","album":"Moon Safari","cover":"https://i/300",
                          "link":"https://open.spotify.com/track/t1","rating":2}]""");
    }

    @Test
    void searchesSongsWithThePersonsRatingOfEach() {
        given(songs.search(CRAIG, "air", 20)).willReturn(List.of(SEXY_BOY, KELLY));
        given(songs.bySong(CRAIG.id())).willReturn(Map.of(SEXY_BOY.songKey(), lovedInGeneral(SEXY_BOY)));

        assertThat(mvc.get().uri("/api/people/{id}/songs/search?q=air&category=none", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"ref":"spotify:t1","rating":3},{"ref":"spotify:t2","rating":null}]""");
    }

    @Test
    void ratesASongForAnActivity() {
        given(songs.rate(any(Person.class), any(Track.class), any(), any(), any())).willAnswer(call -> Optional.of(
                lovedInGeneral(call.getArgument(1)).rated(new ActivityRating(Optional.of(RUNNING.id()), Rating.DISLIKE, "", NOW))));

        assertThat(mvc.post().uri("/api/people/{id}/song-ratings", CRAIG.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"ref":"spotify:t1","artist":"Air","title":"Sexy Boy","album":"Moon Safari",
                         "cover":"https://i/300","categoryId":"%s","rating":1,"comment":"Too slow"}""".formatted(RUNNING.id())))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"ref":"spotify:t1","rating":1}""");
        verify(songs).rate(CRAIG, SEXY_BOY, Optional.of(RUNNING), Optional.of(Rating.DISLIKE), "Too slow");
    }

    @Test
    void aSongWithoutAProperRefIsABadRequest() {
        assertThat(mvc.post().uri("/api/people/{id}/song-ratings", CRAIG.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"ref":"t1","artist":"Air","title":"Sexy Boy","rating":3}"""))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
