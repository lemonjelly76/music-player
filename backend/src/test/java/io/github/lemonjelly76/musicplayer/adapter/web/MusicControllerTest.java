package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.CandidateService;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.LibraryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.NotFoundException;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.RecommendationService;
import io.github.lemonjelly76.musicplayer.application.RecommendationService.Judged;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.candidate.Candidate;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Feedback;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Verdict;
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
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

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
        given(providers.link(any(Person.class), any(AlbumRef.class)))
                .willAnswer(call -> ((AlbumRef) call.getArgument(1)).ids().tidal()
                        .map(id -> URI.create("https://tidal.com/browse/album/" + id)));
        given(providers.link(any(SyncedPlaylist.class)))
                .willAnswer(call -> Optional.of(URI.create("https://tidal.com/browse/playlist/"
                        + ((SyncedPlaylist) call.getArgument(0)).playlistId())));
    }

    @Test
    void listsPicksWithReasonsVerdictsAndLinks() {
        Recommendation pick = Recommendation.create(DUMMY, "Moody trip-hop like your Massive Attack", PickSource.POOL);
        RecommendationList list = RecommendationList.create(CRAIG.id(), Optional.empty(), List.of(pick), NOW)
                .syncedTo(new SyncedPlaylist(Provider.TIDAL, "pl-1"));
        given(recommendations.history(CRAIG, 5)).willReturn(List.of(list));

        assertThat(mvc.get().uri("/api/people/{id}/lists", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"name":"AI Picks","playlistLink":"https://tidal.com/browse/playlist/pl-1",
                          "picks":[{"id":"%s","reason":"Moody trip-hop like your Massive Attack","source":"POOL",
                            "album":{"ref":"tidal:t-9","artist":"Portishead","title":"Dummy","year":1994,
                              "link":"https://tidal.com/browse/album/t-9"}}]}]""".formatted(pick.id()));
    }

    @Test
    void recordsFeedback() {
        Recommendation pick = Recommendation.create(DUMMY, "Moody", PickSource.POOL);
        RecommendationList list = RecommendationList.create(CRAIG.id(), Optional.empty(), List.of(pick), NOW);
        given(recommendations.feedback(pick.id(), Verdict.NOT_FOR_ME, "Too slow")).willReturn(new Judged(list,
                pick.withFeedback(new Feedback(Verdict.NOT_FOR_ME, "Too slow", NOW)), false));

        assertThat(mvc.post().uri("/api/picks/{id}/feedback", pick.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"verdict":"not for me","comment":"Too slow"}"""))
                .hasStatusOk()
                .bodyJson().extractingPath("$.verdict").isEqualTo("NOT_FOR_ME");
    }

    @Test
    void anUnknownVerdictIsABadRequest() {
        assertThat(mvc.post().uri("/api/picks/{id}/feedback", "6f1c2a5e-3b7d-4c8e-9a10-2b3c4d5e6f70")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"verdict":"meh"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.message").asString().contains("Unknown verdict");
    }

    @Test
    void anUnknownPersonIsNotFound() {
        given(people.get(any())).willThrow(new NotFoundException("No person with id x"));

        assertThat(mvc.get().uri("/api/people/{id}/library", "6f1c2a5e-3b7d-4c8e-9a10-2b3c4d5e6f70"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void showsMoreLikeThisWithoutAskingTheModel() {
        given(candidates.moreLikeThis(CRAIG, AlbumRef.of("Massive Attack", "Mezzanine"), 10)).willReturn(List.of(
                new Candidate(DUMMY, 10, new TreeSet<>(List.of("lastfm")), List.of("Massive Attack — Mezzanine"), 3, 0)));

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
}
