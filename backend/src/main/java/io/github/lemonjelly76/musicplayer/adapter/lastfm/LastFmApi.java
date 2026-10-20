package io.github.lemonjelly76.musicplayer.adapter.lastfm;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls Last.fm's REST API. Similarity answers barely change, so they are kept for a day to keep the candidate pool
 * quick and within Last.fm's rate limits.
 */
class LastFmApi {

    static final Duration CACHE_LIFETIME = Duration.ofHours(24);

    private final LastFmProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    LastFmApi(LastFmProperties properties, RestClient.Builder restClientBuilder, Clock clock) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.clock = clock;
    }

    boolean configured() {
        return properties.configured();
    }

    /**
     * The answer to a call, or empty when Last.fm is unreachable or reports an error.
     */
    Optional<JsonNode> call(String method, Map<String, String> params, boolean cacheable) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(properties.apiUrl())
                .queryParam("method", method)
                .queryParam("api_key", properties.apiKey())
                .queryParam("format", "json");
        params.forEach(uri::queryParam);
        String url = uri.encode().toUriString();
        Instant now = clock.instant();
        Cached cached = cacheable ? cache.get(url) : null;
        if (cached != null && cached.at().plus(CACHE_LIFETIME).isAfter(now)) {
            return Optional.of(cached.body());
        }
        Optional<JsonNode> body = fetch(url);
        if (cacheable) {
            body.ifPresent(found -> cache.put(url, new Cached(found, now)));
        }
        return body;
    }

    private Optional<JsonNode> fetch(String url) {
        try {
            String body = restClient.get()
                    .uri(URI.create(url))
                    .header("User-Agent", properties.userAgent())
                    .retrieve()
                    .body(String.class);
            JsonNode json = jsonMapper.readTree(body);
            return json.has("error") ? Optional.empty() : Optional.of(json);
        } catch (RestClientException | JacksonException e) {
            return Optional.empty();
        }
    }

    private record Cached(JsonNode body, Instant at) {
    }
}
