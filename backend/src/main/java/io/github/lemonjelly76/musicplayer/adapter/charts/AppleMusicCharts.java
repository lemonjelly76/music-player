package io.github.lemonjelly76.musicplayer.adapter.charts;

import io.github.lemonjelly76.musicplayer.domain.chart.Chart;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartEntry;
import io.github.lemonjelly76.musicplayer.domain.port.ChartSource;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Apple Music's most played songs and albums in one country, updated daily. Spotify keeps its own charts from apps
 * like this one, and Apple's need no account.
 */
class AppleMusicCharts implements ChartSource {

    private static final int ATTEMPTS = 2;

    private final ChartsProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    AppleMusicCharts(ChartsProperties properties, RestClient.Builder restClientBuilder, Clock clock) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.clock = clock;
    }

    @Override
    public Chart latest() {
        return new Chart(properties.country(), clock.instant(), entries("songs"), entries("albums"));
    }

    private List<ChartEntry> entries(String kind) {
        String url = "%s/%s/music/most-played/%d/%s.json".formatted(properties.url(), properties.country(),
                properties.size(), kind);
        JsonNode results = feed(url).path("feed").path("results");
        List<ChartEntry> entries = new ArrayList<>();
        for (JsonNode result : results) {
            String artist = result.path("artistName").asString("");
            String title = result.path("name").asString("");
            if (!artist.isBlank() && !title.isBlank()) {
                entries.add(new ChartEntry(entries.size() + 1, artist, title));
            }
        }
        return entries;
    }

    /**
     * The feeds now and then take too long to answer, so each is asked twice before giving up.
     */
    private JsonNode feed(String url) {
        RuntimeException failure = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                String body = restClient.get().uri(URI.create(url)).accept(MediaType.APPLICATION_JSON).retrieve()
                        .body(String.class);
                return jsonMapper.readTree(body == null ? "{}" : body);
            } catch (RestClientException | JacksonException e) {
                failure = e;
            }
        }
        throw new MusicProviderUnavailableException("Could not read Apple Music's chart", failure);
    }
}
