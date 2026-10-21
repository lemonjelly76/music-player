package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlayService;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebMvcTest(PlaysController.class)
@Import({SecurityConfiguration.class, ApiErrors.class})
@WithMockUser
class PlaysControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final Person CRAIG = Person.create("Craig", Optional.of(Provider.SPOTIFY));
    private static final Category RUNNING = Category.create("Running", "");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PersonService people;
    @MockitoBean
    private CategoryService categories;
    @MockitoBean
    private PlayService plays;
    @MockitoBean
    private MusicProviders providers;

    @BeforeEach
    void setUp() {
        given(people.get(CRAIG.id())).willReturn(CRAIG);
        given(categories.find(RUNNING.id().toString())).willReturn(RUNNING);
        given(providers.link(any(Person.class), any(AlbumRef.class))).willReturn(Optional.empty());
    }

    @Test
    void summarisesWhatThePersonPlayedOverThePeriod() {
        given(plays.stats(CRAIG, Optional.empty(), 30)).willReturn(Optional.of(List.of(
                new AlbumPlays(AlbumRef.of("Air", "Moon Safari"), 9, NOW),
                new AlbumPlays(AlbumRef.of("Air", "Talkie Walkie"), 3, NOW))));

        assertThat(mvc.get().uri("/api/people/{id}/plays?days=30", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"tracking":true,"days":30,"tracks":12,"albumCount":2,
                         "albums":[{"album":{"title":"Moon Safari"},"plays":9,"lastPlayed":"2026-10-21T12:00:00Z"},
                                   {"album":{"title":"Talkie Walkie"},"plays":3}],
                         "artists":[{"name":"Air","plays":12,"albums":2}]}""");
    }

    @Test
    void anActivityNarrowsThePlaysToItsPicks() {
        given(plays.stats(CRAIG, Optional.of(RUNNING), 28)).willReturn(Optional.of(List.of()));

        assertThat(mvc.get().uri("/api/people/{id}/plays?category={category}", CRAIG.id(), RUNNING.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"tracking":true,"days":28,"tracks":0,"albums":[]}""");
    }

    @Test
    void saysWhenCountingIsOff() {
        given(plays.stats(CRAIG, Optional.empty(), 28)).willReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/people/{id}/plays?category=none", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"tracking":false,"tracks":0,"albums":[],"artists":[]}""");
    }

    @Test
    void aPeriodOutsideTenYearsIsABadRequest() {
        assertThat(mvc.get().uri("/api/people/{id}/plays?days=0", CRAIG.id()))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
