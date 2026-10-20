package io.github.lemonjelly76.musicplayer.adapter.oauth;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Signs a person into their own streaming account with the authorisation code flow and PKCE, keeps their access
 * token fresh, and holds an app-only token for catalogue calls that need no person (ADR-008).
 */
public class ProviderAuthorization {

    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);

    private final Provider provider;
    private final OAuthProperties properties;
    private final RestClient restClient;
    private final ProviderTokenStore store;
    private final Clock clock;
    private final Function<String, Optional<String>> userIdLookup;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private volatile ProviderTokens appToken;

    /**
     * @param userIdLookup the service's own id for whoever holds an access token, read after they connect
     */
    public ProviderAuthorization(Provider provider, OAuthProperties properties, RestClient.Builder restClientBuilder,
            ProviderTokenStore store, Clock clock, Function<String, Optional<String>> userIdLookup) {
        this.provider = provider;
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.store = store;
        this.clock = clock;
        this.userIdLookup = userIdLookup;
    }

    public Provider provider() {
        return provider;
    }

    public boolean registered() {
        return properties.registered();
    }

    public boolean connected(PersonId person) {
        return store.find(person, provider).isPresent();
    }

    public URI connectUrl(PersonId person) {
        requireRegistered();
        dropExpired();
        String state = randomText(16);
        String verifier = randomText(48);
        pending.put(state, new Pending(person, verifier, clock.instant()));
        return UriComponentsBuilder.fromUriString(properties.authorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", properties.scopes())
                .queryParam("state", state)
                .queryParam("code_challenge_method", "S256")
                .queryParam("code_challenge", challenge(verifier))
                .encode()
                .build()
                .toUri();
    }

    /**
     * Finishes signing in with the code the service sent back, and says who connected.
     */
    public PersonId complete(String code, String state) {
        Pending started = state == null ? null : pending.remove(state);
        dropExpired();
        if (started == null || expired(started.issuedAt())) {
            throw new IllegalArgumentException("This sign-in link has expired or was not started here; connect again");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        form.add("code_verifier", started.verifier());
        ProviderTokens tokens = tokens(token(form), Optional.empty(), Optional.empty());
        ProviderTokens identified = new ProviderTokens(
                tokens.userId().or(() -> userIdLookup.apply(tokens.accessToken())),
                tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt());
        store.save(started.person(), provider, identified);
        return started.person();
    }

    public void disconnect(PersonId person) {
        store.clear(person, provider);
    }

    /**
     * The person's access token, refreshed first when about to expire.
     */
    public synchronized String accessToken(PersonId person) {
        ProviderTokens tokens = store.find(person, provider).orElseThrow(() -> new MusicProviderUnavailableException(
                "This person has not connected " + provider.prefix() + " yet; connect it from the app"));
        if (tokens.expireWithin(REFRESH_MARGIN, clock.instant())) {
            tokens = refreshed(person, tokens);
        }
        return tokens.accessToken();
    }

    public Optional<String> userId(PersonId person) {
        return store.find(person, provider).flatMap(ProviderTokens::userId);
    }

    /**
     * A token for the app itself, from the client credentials grant, for searching the catalogue.
     */
    public synchronized String appToken() {
        requireRegistered();
        if (appToken == null || appToken.expireWithin(REFRESH_MARGIN, clock.instant())) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            appToken = tokens(token(form), Optional.empty(), Optional.empty());
        }
        return appToken.accessToken();
    }

    private ProviderTokens refreshed(PersonId person, ProviderTokens tokens) {
        String refreshToken = tokens.refreshToken().orElseThrow(() -> new MusicProviderUnavailableException(
                provider.prefix() + " access has expired; connect it again from the app"));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        try {
            ProviderTokens fresh = tokens(token(form), tokens.userId(), tokens.refreshToken());
            store.save(person, provider, fresh);
            return fresh;
        } catch (RejectedException e) {
            store.clear(person, provider);
            throw new MusicProviderUnavailableException(provider.prefix()
                    + " no longer accepts this app's access; connect it again from the app", e);
        }
    }

    /**
     * Services may leave out the refresh token when it has not changed, so the old one is kept.
     */
    private ProviderTokens tokens(JsonNode body, Optional<String> knownUserId, Optional<String> knownRefreshToken) {
        Optional<String> userId = text(body.path("user_id")).or(() -> knownUserId);
        return new ProviderTokens(userId, body.path("access_token").asString(),
                text(body.path("refresh_token")).or(() -> knownRefreshToken),
                clock.instant().plusSeconds(body.path("expires_in").asLong(3600)));
    }

    private JsonNode token(MultiValueMap<String, String> form) {
        form.add("client_id", properties.clientId());
        try {
            String body = restClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret(),
                            StandardCharsets.UTF_8))
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return jsonMapper.readTree(body);
        } catch (HttpClientErrorException e) {
            throw new RejectedException(provider.prefix() + " refused the sign-in: " + e.getStatusCode(), e);
        } catch (RestClientException | JacksonException e) {
            throw new MusicProviderUnavailableException("Could not reach " + provider.prefix() + " to sign in", e);
        }
    }

    private void requireRegistered() {
        if (!registered()) {
            throw new MusicProviderUnavailableException("The app is not registered with " + provider.prefix()
                    + "; set its client id and secret");
        }
    }

    private void dropExpired() {
        pending.values().removeIf(started -> expired(started.issuedAt()));
    }

    private boolean expired(Instant issuedAt) {
        return issuedAt.plus(STATE_LIFETIME).isBefore(clock.instant());
    }

    private String randomText(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<String> text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? Optional.empty() : Optional.of(node.asString());
    }

    private record Pending(PersonId person, String verifier, Instant issuedAt) {
    }

    static class RejectedException extends MusicProviderUnavailableException {

        RejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
