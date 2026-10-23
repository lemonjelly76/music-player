package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.ArtistService;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtist;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(ArtistController.class)
@Import({SecurityConfiguration.class, ApiErrors.class})
@WithMockUser
class ArtistControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-23T18:00:00Z");
    private static final Person CRAIG = Person.create("Craig", Optional.of(Provider.TIDAL));
    private static final Category RUNNING = Category.create("Running", "");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PersonService people;
    @MockitoBean
    private CategoryService categories;
    @MockitoBean
    private ArtistService artists;

    @BeforeEach
    void setUp() {
        given(people.get(CRAIG.id())).willReturn(CRAIG);
        given(categories.find(RUNNING.id().toString())).willReturn(RUNNING);
    }

    private static LibraryArtist rated(String name, Rating rating) {
        return LibraryArtist.create(CRAIG.id(), name, new ActivityRating(Optional.of(RUNNING.id()), rating, "", NOW),
                LocalDate.of(2026, 10, 23));
    }

    @Test
    void listsTheArtistsRatedForTheActivity() {
        given(artists.artistsFor(CRAIG.id(), Optional.of(RUNNING.id()))).willReturn(List.of(
                rated("Arctic Monkeys", Rating.LOVE), rated("Paramore", Rating.DISLIKE)));

        assertThat(mvc.get().uri("/api/people/{id}/artists?category={c}", CRAIG.id(), RUNNING.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"name":"Arctic Monkeys","rating":3},{"name":"Paramore","rating":1}]""");
    }

    @Test
    void ratesAnArtistForAnActivityWithWhy() {
        given(artists.rate(CRAIG, "Paramore", Optional.of(RUNNING), Optional.of(Rating.DISLIKE), "Too intense"))
                .willReturn(Optional.of(rated("Paramore", Rating.DISLIKE)));

        assertThat(mvc.post().uri("/api/people/{id}/artist-ratings", CRAIG.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"artist":"Paramore","categoryId":"%s","rating":1,"comment":"Too intense"}"""
                        .formatted(RUNNING.id())))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"artist":"Paramore","rating":1}""");
    }

    @Test
    void anArtistNeedsAName() {
        given(artists.rate(CRAIG, null, Optional.empty(), Optional.of(Rating.LOVE), ""))
                .willThrow(new IllegalArgumentException("Say which artist"));

        assertThat(mvc.post().uri("/api/people/{id}/artist-ratings", CRAIG.id())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"rating":3}"""))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
