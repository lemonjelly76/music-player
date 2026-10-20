package io.github.lemonjelly76.musicplayer.adapter.oauth;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProviderAuthorizationTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");
    private static final PersonId CRAIG = PersonId.random();

    private final InMemoryTokenStore store = new InMemoryTokenStore();
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ProviderAuthorization authorization() {
        return new ProviderAuthorization(Provider.SPOTIFY, new OAuthProperties("https://accounts.example/authorize",
                server.baseUrl() + "/token", "client-1", "secret-1", "https://home.example/api/connect/spotify/callback",
                "user-library-read playlist-modify-private"), RestClient.builder(), store, clock,
                token -> Optional.of("user-for-" + token));
    }

    @Test
    void sendsPeopleToSignInWithAStateAndAPkceChallenge() {
        URI url = authorization().connectUrl(CRAIG);

        Map<String, String> query = UriComponentsBuilder.fromUri(url).build().getQueryParams().toSingleValueMap();
        assertThat(url.toString()).startsWith("https://accounts.example/authorize?");
        assertThat(query).containsEntry("response_type", "code")
                .containsEntry("client_id", "client-1")
                .containsEntry("code_challenge_method", "S256")
                .containsKeys("state", "code_challenge");
    }

    @Test
    void exchangesTheCodeWithTheVerifierAndRemembersWhoConnected() throws Exception {
        server.stubFor(post(urlPathEqualTo("/token")).willReturn(okJson("""
                {"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600}""")));
        ProviderAuthorization authorization = authorization();
        Map<String, String> query = UriComponentsBuilder.fromUri(authorization.connectUrl(CRAIG)).build()
                .getQueryParams().toSingleValueMap();

        PersonId connected = authorization.complete("code-1", query.get("state"));

        assertThat(connected).isEqualTo(CRAIG);
        assertThat(store.find(CRAIG, Provider.SPOTIFY)).contains(new ProviderTokens(Optional.of("user-for-access-1"),
                "access-1", Optional.of("refresh-1"), NOW.plusSeconds(3600)));
        String verifier = server.getAllServeEvents().getFirst().getRequest().getBodyAsString()
                .replaceAll(".*code_verifier=([^&]+).*", "$1");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(digest)).isEqualTo(query.get("code_challenge"));
        server.verify(postRequestedFor(urlPathEqualTo("/token"))
                .withHeader("Authorization", equalTo("Basic " + Base64.getEncoder()
                        .encodeToString("client-1:secret-1".getBytes(StandardCharsets.UTF_8))))
                .withRequestBody(containing("grant_type=authorization_code"))
                .withRequestBody(containing("code=code-1")));
    }

    @Test
    void refusesAStateItDidNotIssueOrThatHasExpired() {
        ProviderAuthorization authorization = authorization();
        String state = UriComponentsBuilder.fromUri(authorization.connectUrl(CRAIG)).build().getQueryParams()
                .getFirst("state");

        assertThatIllegalArgumentException().isThrownBy(() -> authorization.complete("code", "made-up"));

        clock = Clock.fixed(NOW.plus(Duration.ofMinutes(11)), ZoneOffset.UTC);
        ProviderAuthorization later = authorization();
        assertThatIllegalArgumentException().isThrownBy(() -> later.complete("code", state));
    }

    @Test
    void refreshesATokenAboutToExpireAndKeepsTheRefreshTokenWhenNoNewOneComes() {
        store.save(CRAIG, Provider.SPOTIFY, new ProviderTokens(Optional.of("u-1"), "old", Optional.of("refresh-1"),
                NOW.plusSeconds(30)));
        server.stubFor(post(urlPathEqualTo("/token")).willReturn(okJson("""
                {"access_token":"new","expires_in":3600}""")));

        assertThat(authorization().accessToken(CRAIG)).isEqualTo("new");

        assertThat(store.find(CRAIG, Provider.SPOTIFY)).contains(new ProviderTokens(Optional.of("u-1"), "new",
                Optional.of("refresh-1"), NOW.plusSeconds(3600)));
        server.verify(postRequestedFor(urlPathEqualTo("/token")).withRequestBody(containing("refresh_token=refresh-1")));
    }

    @Test
    void forgetsAConnectionTheServiceNoLongerAccepts() {
        store.save(CRAIG, Provider.SPOTIFY, new ProviderTokens(Optional.empty(), "old", Optional.of("revoked"),
                NOW.minusSeconds(1)));
        server.stubFor(post(urlPathEqualTo("/token")).willReturn(badRequest()));

        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> authorization().accessToken(CRAIG))
                .withMessageContaining("connect it again");
        assertThat(store.find(CRAIG, Provider.SPOTIFY)).isEmpty();
    }

    @Test
    void someoneWhoNeverConnectedIsToldToConnect() {
        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> authorization().accessToken(CRAIG))
                .withMessageContaining("has not connected spotify");
    }

    @Test
    void reusesTheAppTokenUntilItExpires() {
        server.stubFor(post(urlPathEqualTo("/token")).willReturn(okJson("""
                {"access_token":"app","expires_in":3600}""")));
        ProviderAuthorization authorization = authorization();

        assertThat(authorization.appToken()).isEqualTo("app");
        assertThat(authorization.appToken()).isEqualTo("app");

        server.verify(1, postRequestedFor(urlPathEqualTo("/token")).withRequestBody(containing("grant_type=client_credentials")));
    }
}
