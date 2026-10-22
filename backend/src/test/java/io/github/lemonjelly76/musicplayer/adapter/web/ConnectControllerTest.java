package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(ConnectController.class)
@Import({SecurityConfiguration.class, ApiErrors.class})
class ConnectControllerTest {

    private static final Person CRAIG = Person.create("Craig", Optional.of(Provider.TIDAL));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PersonService people;
    @MockitoBean(name = "tidalAuthorization")
    private ProviderAuthorization tidal;
    @MockitoBean(name = "spotifyAuthorization")
    private ProviderAuthorization spotify;

    @BeforeEach
    void setUp() {
        given(tidal.provider()).willReturn(Provider.TIDAL);
        given(spotify.provider()).willReturn(Provider.SPOTIFY);
        given(people.get(CRAIG.id())).willReturn(CRAIG);
    }

    @Test
    @WithMockUser
    void connectsASecondServiceToo() {
        given(spotify.connectUrl(CRAIG.id())).willReturn(URI.create("https://accounts.spotify.com/authorize?state=s"));

        assertThat(mvc.get().uri("/api/people/{id}/connect?service=spotify", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isEqualTo("""
                        {"service":"spotify","url":"https://accounts.spotify.com/authorize?state=s"}""");
    }

    @Test
    @WithMockUser
    void handsTheAppTheSignInLinkForThePersonsService() {
        given(tidal.connectUrl(CRAIG.id())).willReturn(URI.create("https://login.tidal.com/authorize?state=s"));

        assertThat(mvc.get().uri("/api/people/{id}/connect", CRAIG.id()))
                .hasStatusOk()
                .bodyJson().isEqualTo("""
                        {"service":"tidal","url":"https://login.tidal.com/authorize?state=s"}""");
    }

    @Test
    @WithAnonymousUser
    void theCallbackNeedsNoLoginAndSaysWhoConnected() {
        given(tidal.complete("code-1", "state-1")).willReturn(CRAIG.id());

        assertThat(mvc.get().uri("/api/connect/tidal/callback?code=code-1&state=state-1"))
                .hasStatusOk()
                .bodyText().contains("Craig&#39;s tidal account is connected");
    }

    @Test
    @WithAnonymousUser
    void aStaleCallbackIsRefusedPolitely() {
        willThrow(new IllegalArgumentException("This sign-in link has expired")).given(tidal).complete("c", "old");

        assertThat(mvc.get().uri("/api/connect/tidal/callback?code=c&state=old"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyText().contains("This sign-in link has expired");
    }

    @Test
    @WithAnonymousUser
    void startingASignInNeedsTheLogin() {
        assertThat(mvc.get().uri("/api/people/{id}/connect", CRAIG.id())).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
