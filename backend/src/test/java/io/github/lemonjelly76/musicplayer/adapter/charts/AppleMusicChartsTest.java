package io.github.lemonjelly76.musicplayer.adapter.charts;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.lemonjelly76.musicplayer.domain.chart.Chart;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartEntry;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AppleMusicChartsTest {

    @RegisterExtension
    static WireMockExtension apple = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final Instant NOW = Instant.parse("2026-10-21T09:00:00Z");

    private AppleMusicCharts charts() {
        return new AppleMusicCharts(new ChartsProperties(apple.baseUrl() + "/api/v2", "gb", 50), RestClient.builder(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void readsTheCountrysMostPlayedSongsAndAlbumsInOrder() {
        apple.stubFor(get(urlPathEqualTo("/api/v2/gb/music/most-played/50/songs.json")).willReturn(okJson("""
                {"feed":{"title":"Top Songs","results":[
                  {"artistName":"Tate McRae","name":"Housewife"},
                  {"artistName":"","name":"Nameless"},
                  {"artistName":"Jamie Miller","name":"Babydoll"}]}}""")));
        apple.stubFor(get(urlPathEqualTo("/api/v2/gb/music/most-played/50/albums.json")).willReturn(okJson("""
                {"feed":{"title":"Top Albums","results":[{"artistName":"Fontaines D.C.","name":"Dopamine Chamber"}]}}""")));

        Chart chart = charts().latest();

        assertThat(chart.country()).isEqualTo("gb");
        assertThat(chart.fetchedAt()).isEqualTo(NOW);
        assertThat(chart.songs()).containsExactly(new ChartEntry(1, "Tate McRae", "Housewife"),
                new ChartEntry(2, "Jamie Miller", "Babydoll"));
        assertThat(chart.albums()).containsExactly(new ChartEntry(1, "Fontaines D.C.", "Dopamine Chamber"));
    }

    @Test
    void triesAgainOnceWhenAFeedDoesNotAnswer() {
        apple.stubFor(get(urlPathEqualTo("/api/v2/gb/music/most-played/50/songs.json")).inScenario("slow")
                .whenScenarioStateIs(Scenario.STARTED).willReturn(serverError()).willSetStateTo("recovered"));
        apple.stubFor(get(urlPathEqualTo("/api/v2/gb/music/most-played/50/songs.json")).inScenario("slow")
                .whenScenarioStateIs("recovered").willReturn(okJson("""
                        {"feed":{"results":[{"artistName":"Tate McRae","name":"Housewife"}]}}""")));
        apple.stubFor(get(urlPathEqualTo("/api/v2/gb/music/most-played/50/albums.json")).willReturn(okJson("""
                {"feed":{"results":[]}}""")));

        assertThat(charts().latest().songs()).hasSize(1);
    }

    @Test
    void saysSoWhenTheChartCannotBeRead() {
        apple.stubFor(get(urlPathEqualTo("/api/v2/gb/music/most-played/50/songs.json")).willReturn(serverError()));

        assertThatExceptionOfType(MusicProviderUnavailableException.class).isThrownBy(() -> charts().latest());
    }
}
