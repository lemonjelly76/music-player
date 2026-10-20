package io.github.lemonjelly76.musicplayer.adapter.tidal;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.lemonjelly76.musicplayer.adapter.oauth.InMemoryTokenStore;
import io.github.lemonjelly76.musicplayer.adapter.oauth.OAuthProperties;
import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderTokens;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.havingExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class TidalMusicProviderTest {

    @RegisterExtension
    static WireMockExtension tidal = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");
    private static final PersonId CRAIG = PersonId.random();

    private static final String ALBUMS = """
            {"data":[
              {"id":"77","type":"albums","attributes":{"title":"Blue","releaseDate":"1971-06-22"},
               "relationships":{"artists":{"data":[{"id":"a1","type":"artists"}]}}},
              {"id":"78","type":"albums","attributes":{"title":"Hejira","releaseDate":"1976-11-22"},
               "relationships":{"artists":{"data":[{"id":"a1","type":"artists"}]}}}],
             "included":[{"id":"a1","type":"artists","attributes":{"name":"Joni Mitchell"}}]}""";

    private final InMemoryTokenStore store = new InMemoryTokenStore();
    private TidalMusicProvider provider;

    @BeforeEach
    void setUp() {
        TidalProperties properties = new TidalProperties(tidal.baseUrl() + "/v2", "GB", "https://tidal.com/browse",
                new OAuthProperties("https://login.example/authorize", tidal.baseUrl() + "/token", "client", "secret",
                        "https://home.example/callback", "playlists.write"));
        ProviderAuthorization authorization = new ProviderAuthorization(Provider.TIDAL, properties.oauth(),
                RestClient.builder(), store, Clock.fixed(NOW, ZoneOffset.UTC), token -> Optional.empty());
        provider = new TidalMusicProvider(properties, authorization, RestClient.builder());
        store.save(CRAIG, Provider.TIDAL, new ProviderTokens(Optional.of("u-42"), "user-token", Optional.empty(),
                NOW.plusSeconds(3600)));
        tidal.stubFor(post(urlPathEqualTo("/token")).willReturn(okJson("""
                {"access_token":"app-token","expires_in":3600}""")));
    }

    private static AlbumRef blue() {
        return new AlbumRef("Joni Mitchell", "Blue", Optional.of(1971), ProviderIds.of(Provider.TIDAL, "77"));
    }

    @Test
    void searchesAlbumsAndReadsTheirArtistsAndYears() {
        tidal.stubFor(get(urlPathEqualTo("/v2/searchResults/joni%20mitchell/relationships/albums"))
                .willReturn(okJson("""
                        {"data":[{"id":"77","type":"albums"},{"id":"78","type":"albums"}]}""")));
        tidal.stubFor(get(urlPathEqualTo("/v2/albums")).willReturn(okJson(ALBUMS)));

        List<AlbumRef> found = provider.searchAlbums("joni mitchell", 5);

        assertThat(found).containsExactly(blue(),
                new AlbumRef("Joni Mitchell", "Hejira", Optional.of(1976), ProviderIds.of(Provider.TIDAL, "78")));
        tidal.verify(getRequestedFor(urlPathEqualTo("/v2/albums"))
                .withQueryParam("filter[id]", havingExactly("77", "78"))
                .withQueryParam("include", equalTo("artists"))
                .withQueryParam("countryCode", equalTo("GB"))
                .withHeader("Authorization", equalTo("Bearer app-token")));
    }

    @Test
    void findsTheAlbumMatchingAnArtistAndTitle() {
        tidal.stubFor(get(urlPathEqualTo("/v2/searchResults/Joni%20Mitchell%20Hejira/relationships/albums"))
                .willReturn(okJson("""
                        {"data":[{"id":"77","type":"albums"},{"id":"78","type":"albums"}]}""")));
        tidal.stubFor(get(urlPathEqualTo("/v2/albums")).willReturn(okJson(ALBUMS)));

        assertThat(provider.findAlbum("Joni Mitchell", "Hejira")).map(AlbumRef::title).contains("Hejira");
    }

    @Test
    void savesAFavouriteToThePersonsCollection() {
        tidal.stubFor(post(urlPathEqualTo("/v2/userCollections/u-42/relationships/albums")).willReturn(okJson("{}")));

        provider.addFavourite(CRAIG, "77");

        tidal.verify(postRequestedFor(urlPathEqualTo("/v2/userCollections/u-42/relationships/albums"))
                .withHeader("Authorization", equalTo("Bearer user-token"))
                .withRequestBody(equalToJson("""
                        {"data":[{"id":"77","type":"albums"}]}""")));
    }

    @Test
    void replacesThePlaylistWithEveryTrackOfEachAlbum() {
        tidal.stubFor(delete(urlPathEqualTo("/v2/playlists/old")).willReturn(notFound()));
        tidal.stubFor(post(urlPathEqualTo("/v2/playlists")).willReturn(okJson("""
                {"data":{"id":"new-playlist","type":"playlists"}}""")));
        tidal.stubFor(get(urlPathEqualTo("/v2/albums/77/relationships/items"))
                .withQueryParam("countryCode", equalTo("GB"))
                .willReturn(okJson("""
                        {"data":[{"id":"t1","type":"tracks"},{"id":"t2","type":"tracks"}],
                         "links":{"next":"/v2/albums/77/relationships/items?page%5Bcursor%5D=c2&countryCode=GB"}}""")));
        tidal.stubFor(get(urlPathEqualTo("/v2/albums/77/relationships/items"))
                .withQueryParam("page[cursor]", equalTo("c2"))
                .willReturn(okJson("""
                        {"data":[{"id":"t3","type":"tracks"}],"links":{}}""")));
        tidal.stubFor(post(urlPathEqualTo("/v2/playlists/new-playlist/relationships/items")).willReturn(okJson("{}")));

        String id = provider.replacePlaylist(CRAIG, Optional.of("old"), "AI Picks", "Picked for Craig", List.of("77"));

        assertThat(id).isEqualTo("new-playlist");
        tidal.verify(deleteRequestedFor(urlPathEqualTo("/v2/playlists/old")));
        tidal.verify(postRequestedFor(urlPathEqualTo("/v2/playlists")).withRequestBody(equalToJson("""
                {"data":{"type":"playlists","attributes":{"name":"AI Picks","description":"Picked for Craig",
                  "accessType":"PUBLIC"}}}""")));
        tidal.verify(postRequestedFor(urlPathEqualTo("/v2/playlists/new-playlist/relationships/items"))
                .withRequestBody(equalToJson("""
                        {"data":[{"id":"t1","type":"tracks"},{"id":"t2","type":"tracks"},{"id":"t3","type":"tracks"}]}""")));
    }

    @Test
    void linksOpenTheAlbumAndPlaylistOnTidal() {
        assertThat(provider.albumLink("77")).isEqualTo(URI.create("https://tidal.com/browse/album/77"));
        assertThat(provider.playlistLink("p1")).isEqualTo(URI.create("https://tidal.com/browse/playlist/p1"));
    }
}
