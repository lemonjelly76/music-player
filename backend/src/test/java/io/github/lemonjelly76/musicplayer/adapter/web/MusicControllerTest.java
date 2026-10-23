package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.CandidateService;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.LibraryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.NotFoundException;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.RecommendationService;
import io.github.lemonjelly76.musicplayer.application.ServiceLink;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.AudioFormat;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.candidate.Candidate;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbumId;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationId;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationListId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
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
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(MusicController.class)
@Import({SecurityConfiguration.class, ApiErrors.class})
@WithMockUser
class MusicControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");
    private static final Person CRAIG = Person.create("Craig", Optional.of(Provider.TIDAL));
    private static final AlbumRef DUMMY = new AlbumRef("Portishead", "Dummy", Optional.of(1994),
            ProviderIds.of(Provider.TIDAL, "t-9"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PersonService people;
    @MockitoBean
    private CategoryService categories;
    @MockitoBean
    private LibraryService library;
    @MockitoBean
    private RecommendationService recommendations;
    @MockitoBean
    private CandidateService candidates;
    @MockitoBean
    private MusicProviders providers;

    @BeforeEach
    void setUp() {
        given(people.get(CRAIG.id())).willReturn(CRAIG);
        given(recommendations.withCovers(any(Person.class), any(RecommendationList.class), anyInt()))
                .willAnswer(call -> call.getArgument(1));
        given(providers.link(any(Person.class), any(AlbumRef.class)))
                .willAnswer(call -> ((AlbumRef) call.getArgument(1)).ids().tidal()
                        .map(id -> URI.create("https://tidal.com/browse/album/" + id)));
    }

    @Test
    void listsAlbumsToTryWithReasonsAndLinks() {
        Recommendation pick = Recommendation.create(DUMMY, "Moody trip-hop like your Massive Attack", PickSource.POOL);
        RecommendationList list = RecommendationList.create(CRAIG.id(), Optional.empty(), List.of(pick), NOW);
        given(recommendations.history(CRAIG, 5)).willReturn(List.of(list));

        assertThat(mvc.get().uri("/api/people/{id}/lists", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"name":"AI Picks — Mainstream",
                          "picks":[{"id":"%s","reason":"Moody trip-hop like your Massive Attack","source":"POOL",
                            "album":{"ref":"tidal:t-9","artist":"Portishead","title":"Dummy","year":1994,
                              "link":"https://tidal.com/browse/album/t-9"}}]}]""".formatted(pick.id()));
    }

    @Test
    void ratesAnAlbumForAnActivity() {
        LibraryAlbum rated = LibraryAlbum.create(CRAIG.id(), DUMMY, ActivityRating.mainstream(Rating.LOVE, NOW), "",
                List.of(), LocalDate.of(2026, 10, 20));
        given(library.rate(CRAIG, "tidal:t-9", Optional.empty(), Optional.of(Rating.LOVE), "On repeat"))
                .willReturn(Optional.of(rated));

        assertThat(mvc.post().uri("/api/people/{id}/ratings", CRAIG.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"albumRef":"tidal:t-9","categoryId":"none","rating":3,"comment":"On repeat"}"""))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"albumRef":"tidal:t-9","rating":3}""");
    }

    @Test
    void aRatingOutsideOneToThreeIsABadRequest() {
        assertThat(mvc.post().uri("/api/people/{id}/ratings", CRAIG.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"albumRef":"tidal:t-9","rating":5}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.message").asString().contains("1 (dislike), 2 (ok) or 3 (love)");
    }

    @Test
    void showsOnlyTheActivitysOwnRating() {
        Category running = Category.create("Running", "");
        LibraryAlbum loved = LibraryAlbum.create(CRAIG.id(), DUMMY, ActivityRating.mainstream(Rating.LOVE, NOW), "",
                List.of(), LocalDate.of(2026, 10, 20));
        given(categories.find(running.id().toString())).willReturn(running);
        given(library.libraryFor(any(), any(), any())).willReturn(List.of(loved));

        assertThat(mvc.get().uri("/api/people/{id}/library?category=none", CRAIG.id()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"rating":3}]""");
        assertThat(mvc.get().uri("/api/people/{id}/library?category={c}", CRAIG.id(), running.id()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"rating":null}]""");
    }

    @Test
    void listsTheAlbumsNotForThePersonForAnActivity() {
        Category running = Category.create("Running", "");
        LibraryAlbum disliked = LibraryAlbum.create(CRAIG.id(), DUMMY,
                new ActivityRating(Optional.of(running.id()), Rating.DISLIKE, "", NOW), "", List.of(),
                LocalDate.of(2026, 10, 20));
        given(categories.find(running.id().toString())).willReturn(running);
        given(library.dislikedFor(CRAIG.id(), Optional.of(running.id()))).willReturn(List.of(disliked));

        assertThat(mvc.get().uri("/api/people/{id}/library/disliked?category={c}", CRAIG.id(), running.id()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"album":{"ref":"tidal:t-9"},"rating":1}]""");
    }

    @Test
    void listsTheAlbumsStillToSortForAnActivity() {
        Category running = Category.create("Running", "");
        LibraryAlbum loved = LibraryAlbum.create(CRAIG.id(), DUMMY, ActivityRating.mainstream(Rating.LOVE, NOW), "",
                List.of(), LocalDate.of(2026, 10, 20));
        given(categories.find(running.id().toString())).willReturn(running);
        given(library.toSortFor(CRAIG.id(), Optional.of(running.id()))).willReturn(List.of(loved));

        assertThat(mvc.get().uri("/api/people/{id}/library/to-sort?category={c}", CRAIG.id(), running.id()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"album":{"title":"Dummy"},"rating":null}]""");
    }

    @Test
    void searchesTheCatalogueWithThePersonsRatingOfEachAlbumForTheActivity() {
        AlbumRef third = new AlbumRef("Portishead", "Third", Optional.of(2008), ProviderIds.of(Provider.TIDAL, "t-3"));
        LibraryAlbum loved = LibraryAlbum.create(CRAIG.id(), DUMMY, ActivityRating.mainstream(Rating.LOVE, NOW), "",
                List.of(), LocalDate.of(2026, 10, 20));
        given(providers.search(CRAIG, "Portishead", 20)).willReturn(List.of(DUMMY, third));
        given(library.byKey(CRAIG.id())).willReturn(Map.of(DUMMY.key(), loved));

        assertThat(mvc.get().uri("/api/people/{id}/search?q=Portishead&category=none", CRAIG.id()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"album":{"ref":"tidal:t-9","title":"Dummy","link":"https://tidal.com/browse/album/t-9"},
                          "rating":3},
                         {"album":{"ref":"tidal:t-3","title":"Third"},"rating":null}]""");
    }

    @Test
    void showsTheFormatsAnAlbumCanBePlayedInAndWhereElseItOpens() {
        AlbumRef third = new AlbumRef("Portishead", "Third", Optional.of(2008), ProviderIds.of(Provider.TIDAL, "t-3"),
                Optional.empty(), Set.of(AudioFormat.DOLBY_ATMOS, AudioFormat.LOSSLESS));
        given(providers.search(CRAIG, "Third", 20)).willReturn(List.of(third));
        given(providers.elsewhere(CRAIG, third)).willReturn(List.of(
                new ServiceLink(Provider.SPOTIFY, URI.create("https://open.spotify.com/search/Portishead%20Third"))));

        assertThat(mvc.get().uri("/api/people/{id}/search?q=Third&category=none", CRAIG.id()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"album":{"ref":"tidal:t-3","formats":["LOSSLESS","DOLBY_ATMOS"],
                          "elsewhere":[{"service":"spotify","link":"https://open.spotify.com/search/Portishead%20Third"}]}}]""");
    }

    @Test
    void aBlankSearchIsABadRequest() {
        assertThat(mvc.get().uri("/api/people/{id}/search?q= ", CRAIG.id()))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnknownPersonIsNotFound() {
        given(people.get(any())).willThrow(new NotFoundException("No person with id x"));

        assertThat(mvc.get().uri("/api/people/{id}/library", "6f1c2a5e-3b7d-4c8e-9a10-2b3c4d5e6f70"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void showsMoreLikeThisWithoutAskingTheModel() {
        given(candidates.moreLikeThis(CRAIG, AlbumRef.of("Massive Attack", "Mezzanine"), Optional.empty(), 10)).willReturn(List.of(
                new Candidate(DUMMY, 10, new TreeSet<>(List.of("lastfm")), List.of("Massive Attack — Mezzanine"), 0, List.of())));

        assertThat(mvc.get().uri("/api/people/{id}/more-like-this?artist=Massive Attack&title=Mezzanine", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"album":{"title":"Dummy","link":"https://tidal.com/browse/album/t-9"},"score":10.0,
                          "sources":["lastfm"],"seeds":["Massive Attack — Mezzanine"]}]""");
    }

    @Test
    @WithAnonymousUser
    void needsTheHouseholdLogin() {
        assertThat(mvc.get().uri("/api/people/{id}/library", CRAIG.id())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deletesAListOfAlbumsToTryOrOnePickFromIt() {
        RecommendationListId list = RecommendationListId.random();
        RecommendationId pick = RecommendationId.random();

        assertThat(mvc.delete().uri("/api/people/{id}/lists/{list}", CRAIG.id(), list)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/people/{id}/lists/{list}/picks/{pick}", CRAIG.id(), list, pick))
                .hasStatus(HttpStatus.NO_CONTENT);

        then(recommendations).should().delete(CRAIG, list);
        then(recommendations).should().removePick(CRAIG, list, pick);
    }

    @Test
    void removesAnAlbumFromTheLibrary() {
        LibraryAlbumId album = LibraryAlbumId.random();

        assertThat(mvc.delete().uri("/api/people/{id}/library/{album}", CRAIG.id(), album))
                .hasStatus(HttpStatus.NO_CONTENT);

        then(library).should().remove(CRAIG.id(), album);
    }
}
