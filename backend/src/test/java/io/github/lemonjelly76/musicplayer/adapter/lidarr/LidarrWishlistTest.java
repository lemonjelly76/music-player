package io.github.lemonjelly76.musicplayer.adapter.lidarr;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class LidarrWishlistTest {

    @RegisterExtension
    static WireMockExtension lidarr = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final LidarrWishlist wishlist = new LidarrWishlist(
            new LidarrProperties(lidarr.baseUrl(), "key-1", "/music", 2, 3), RestClient.builder());

    @Test
    void addsANewAlbumWithItsArtistUnmonitoredOtherwise() {
        lidarr.stubFor(get(urlPathEqualTo("/api/v1/album/lookup")).withQueryParam("term", equalTo("lidarr:rg-1"))
                .willReturn(okJson("""
                        [{"title":"Bookends","foreignAlbumId":"rg-1",
                          "artist":{"artistName":"Simon & Garfunkel","foreignArtistId":"ar-1"}}]""")));
        lidarr.stubFor(post(urlPathEqualTo("/api/v1/album")).willReturn(okJson("{}")));

        String result = wishlist.want(new AlbumRef("Simon & Garfunkel", "Bookends", Optional.empty(),
                ProviderIds.musicBrainz("rg-1")));

        assertThat(result).contains("added to Lidarr");
        lidarr.verify(postRequestedFor(urlPathEqualTo("/api/v1/album"))
                .withHeader("X-Api-Key", equalTo("key-1"))
                .withRequestBody(equalToJson("""
                        {"title":"Bookends","foreignAlbumId":"rg-1","monitored":true,
                         "addOptions":{"searchForNewAlbum":true},
                         "artist":{"artistName":"Simon & Garfunkel","foreignArtistId":"ar-1","qualityProfileId":2,
                           "metadataProfileId":3,"rootFolderPath":"/music","monitored":true,"monitorNewItems":"none",
                           "addOptions":{"monitor":"none","searchForMissingAlbums":false}}}""")));
    }

    @Test
    void monitorsAndSearchesForAnAlbumLidarrAlreadyLists() {
        lidarr.stubFor(get(urlPathEqualTo("/api/v1/album/lookup")).withQueryParam("term", equalTo("Air Moon Safari"))
                .willReturn(okJson("""
                        [{"id":12,"title":"Moon Safari","artist":{"id":4,"artistName":"Air"}}]""")));
        lidarr.stubFor(put(urlPathEqualTo("/api/v1/album/monitor")).willReturn(okJson("[]")));
        lidarr.stubFor(post(urlPathEqualTo("/api/v1/command")).willReturn(okJson("{}")));

        assertThat(wishlist.want(AlbumRef.of("Air", "Moon Safari"))).contains("already in Lidarr");

        lidarr.verify(putRequestedFor(urlPathEqualTo("/api/v1/album/monitor")).withRequestBody(equalToJson("""
                {"albumIds":[12],"monitored":true}""")));
        lidarr.verify(postRequestedFor(urlPathEqualTo("/api/v1/command")).withRequestBody(equalToJson("""
                {"name":"AlbumSearch","albumIds":[12]}""")));
    }
}
