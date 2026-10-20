package io.github.lemonjelly76.musicplayer.adapter.musicbrainz;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

class MusicBrainzCatalogueTest {

    @RegisterExtension
    static WireMockExtension musicBrainz = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final MusicBrainzCatalogue catalogue = new MusicBrainzCatalogue(
            new MusicBrainzProperties(musicBrainz.baseUrl() + "/ws/2", "music-player-test/1.0 ( test )"),
            RestClient.builder());

    @Test
    void findsTheReleaseGroupJoiningTheArtistCredits() {
        musicBrainz.stubFor(get(urlPathEqualTo("/ws/2/release-group")).willReturn(okJson("""
                {"release-groups":[
                  {"id":"rg-tribute","title":"Bookends","artist-credit":[{"name":"Various Artists"}]},
                  {"id":"rg-1","title":"Bookends","first-release-date":"1968-04-03",
                   "artist-credit":[{"name":"Simon","joinphrase":" & "},{"name":"Garfunkel"}]}]}""")));

        assertThat(catalogue.findAlbum("Simon & Garfunkel", "Bookends")).contains(new AlbumRef("Simon & Garfunkel",
                "Bookends", Optional.of(1968), ProviderIds.musicBrainz("rg-1")));
        musicBrainz.verify(getRequestedFor(urlPathEqualTo("/ws/2/release-group"))
                .withQueryParam("query", equalTo("releasegroup:\"Bookends\" AND artist:\"Simon & Garfunkel\""))
                .withHeader("User-Agent", equalTo("music-player-test/1.0 ( test )")));
    }

    @Test
    void triesOnceMoreWhenMusicBrainzIsBusy() {
        musicBrainz.stubFor(get(urlPathEqualTo("/ws/2/release-group/rg-1")).inScenario("busy")
                .whenScenarioStateIs(STARTED).willReturn(serviceUnavailable()).willSetStateTo("calm"));
        musicBrainz.stubFor(get(urlPathEqualTo("/ws/2/release-group/rg-1")).inScenario("busy")
                .whenScenarioStateIs("calm").willReturn(okJson("""
                        {"id":"rg-1","title":"Bookends","artist-credit":[{"name":"Simon & Garfunkel"}]}""")));

        assertThat(catalogue.byMusicBrainzId("rg-1")).map(AlbumRef::title).contains("Bookends");
    }

    @Test
    void anUnknownIdIsNotFound() {
        musicBrainz.stubFor(get(urlPathEqualTo("/ws/2/release-group/nope")).willReturn(notFound()));

        assertThat(catalogue.byMusicBrainzId("nope")).isEmpty();
    }
}
