package io.github.lemonjelly76.musicplayer.adapter.tidal;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.lemonjelly76.musicplayer.adapter.oauth.InMemoryTokenStore;
import io.github.lemonjelly76.musicplayer.adapter.oauth.OAuthProperties;
import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderTokens;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.AudioFormat;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.havingExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
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

    /**
     * Tidal answers a search with one searchResults resource whose relationship lists what it found.
     */
    private static String searchResults(String type, String... ids) {
        String found = String.join(",", Arrays.stream(ids)
                .map(id -> "{\"id\":\"" + id + "\",\"type\":\"" + type + "\"}").toList());
        return "{\"data\":[{\"id\":\"opaque\",\"type\":\"searchResults\",\"relationships\":{\""
                + type + "\":{\"data\":[" + found + "]}}}]}";
    }

    private static AlbumRef blue() {
        return new AlbumRef("Joni Mitchell", "Blue", Optional.of(1971), ProviderIds.of(Provider.TIDAL, "77"));
    }

    @Test
    void searchesAlbumsAndReadsTheirArtistsAndYears() {
        tidal.stubFor(get(urlPathEqualTo("/v2/searchResults")).willReturn(okJson(searchResults("albums", "77", "78"))));
        tidal.stubFor(get(urlPathEqualTo("/v2/albums")).willReturn(okJson(ALBUMS)));

        List<AlbumRef> found = provider.searchAlbums("joni mitchell", 5);

        assertThat(found).containsExactly(blue(),
                new AlbumRef("Joni Mitchell", "Hejira", Optional.of(1976), ProviderIds.of(Provider.TIDAL, "78")));
        tidal.verify(getRequestedFor(urlPathEqualTo("/v2/searchResults"))
                .withQueryParam("filter[query]", equalTo("joni mitchell"))
                .withQueryParam("include", equalTo("albums"))
                .withQueryParam("countryCode", equalTo("GB")));
        tidal.verify(getRequestedFor(urlPathEqualTo("/v2/albums"))
                .withQueryParam("filter[id]", havingExactly("77", "78"))
                .withQueryParam("include", equalTo("artists,coverArt"))
                .withQueryParam("countryCode", equalTo("GB"))
                .withHeader("Authorization", equalTo("Bearer app-token")));
    }

    @Test
    void findsTheAlbumMatchingAnArtistAndTitle() {
        tidal.stubFor(get(urlPathEqualTo("/v2/searchResults")).withQueryParam("filter[query]",
                equalTo("Joni Mitchell Hejira")).willReturn(okJson(searchResults("albums", "77", "78"))));
        tidal.stubFor(get(urlPathEqualTo("/v2/albums")).willReturn(okJson(ALBUMS)));

        assertThat(provider.findAlbum("Joni Mitchell", "Hejira")).map(AlbumRef::title).contains("Hejira");
    }

    @Test
    void savesAFavouriteToThePersonsCollection() {
        tidal.stubFor(post(urlPathEqualTo("/v2/userCollectionAlbums/me/relationships/items")).willReturn(okJson("{}")));

        provider.addFavourite(CRAIG, "77");

        tidal.verify(postRequestedFor(urlPathEqualTo("/v2/userCollectionAlbums/me/relationships/items"))
                .withHeader("Authorization", equalTo("Bearer user-token"))
                .withRequestBody(equalToJson("""
                        {"data":[{"id":"77","type":"albums"}]}""")));
    }

    @Test
    void readsEveryPageOfThePersonsFavouriteAlbums() {
        tidal.stubFor(get(urlPathEqualTo("/v2/userCollectionAlbums/me/relationships/items"))
                .withQueryParam("page[cursor]", absent()).willReturn(okJson("""
                        {"data":[{"id":"77","type":"albums"}],
                         "links":{"next":"/userCollectionAlbums/me/relationships/items?page%5Bcursor%5D=c2"}}""")));
        tidal.stubFor(get(urlPathEqualTo("/v2/userCollectionAlbums/me/relationships/items"))
                .withQueryParam("page[cursor]", equalTo("c2")).willReturn(okJson("""
                        {"data":[{"id":"78","type":"albums"}],"links":{}}""")));
        tidal.stubFor(get(urlPathEqualTo("/v2/albums")).willReturn(okJson(ALBUMS)));

        assertThat(provider.favouriteAlbums(CRAIG)).extracting(AlbumRef::title).containsExactly("Blue", "Hejira");
        tidal.verify(getRequestedFor(urlPathEqualTo("/v2/userCollectionAlbums/me/relationships/items"))
                .withHeader("Authorization", equalTo("Bearer user-token")));
    }

    @Test
    void replacesThePlaylistWithTheSongsInBatches() {
        tidal.stubFor(delete(urlPathEqualTo("/v2/playlists/old")).willReturn(notFound()));
        tidal.stubFor(post(urlPathEqualTo("/v2/playlists")).willReturn(okJson("""
                {"data":{"id":"new-playlist","type":"playlists"}}""")));
        tidal.stubFor(post(urlPathEqualTo("/v2/playlists/new-playlist/relationships/items")).willReturn(okJson("{}")));
        List<String> songs = IntStream.rangeClosed(1, 21).mapToObj(n -> "t" + n).toList();

        String id = provider.replacePlaylist(CRAIG, Optional.of("old"), "AI Picks", "Picked for Craig", songs);

        assertThat(id).isEqualTo("new-playlist");
        tidal.verify(deleteRequestedFor(urlPathEqualTo("/v2/playlists/old")));
        tidal.verify(postRequestedFor(urlPathEqualTo("/v2/playlists")).withRequestBody(equalToJson("""
                {"data":{"type":"playlists","attributes":{"name":"AI Picks","description":"Picked for Craig",
                  "accessType":"PUBLIC"}}}""")));
        tidal.verify(2, postRequestedFor(urlPathEqualTo("/v2/playlists/new-playlist/relationships/items")));
        tidal.verify(postRequestedFor(urlPathEqualTo("/v2/playlists/new-playlist/relationships/items"))
                .withRequestBody(equalToJson("""
                        {"data":[{"id":"t21","type":"tracks"}]}""")));
    }

    @Test
    void renamesAPlaylistInThePersonsAccount() {
        tidal.stubFor(patch(urlPathEqualTo("/v2/playlists/pl-1")).willReturn(okJson("{}")));

        provider.renamePlaylist(CRAIG, "pl-1", "AI Picks — Jogging");

        tidal.verify(patchRequestedFor(urlPathEqualTo("/v2/playlists/pl-1"))
                .withHeader("Authorization", equalTo("Bearer user-token"))
                .withRequestBody(equalToJson("""
                        {"data":{"id":"pl-1","type":"playlists","attributes":{"name":"AI Picks — Jogging"}}}""")));
    }

    @Test
    void searchesSongsWithTheirAlbumsCovers() {
        tidal.stubFor(get(urlPathEqualTo("/v2/searchResults")).willReturn(okJson(searchResults("tracks", "s1"))));
        tidal.stubFor(get(urlPathEqualTo("/v2/tracks")).willReturn(okJson("""
                {"data":[{"id":"s1","type":"tracks","attributes":{"title":"River"},
                   "relationships":{"artists":{"data":[{"id":"a1","type":"artists"}]},
                                    "albums":{"data":[{"id":"77","type":"albums"}]}}}],
                 "included":[{"id":"a1","type":"artists","attributes":{"name":"Joni Mitchell"}},
                             {"id":"77","type":"albums","attributes":{"title":"Blue"}}]}""")));
        tidal.stubFor(get(urlPathEqualTo("/v2/albums")).willReturn(okJson("""
                {"data":[{"id":"77","type":"albums","attributes":{"title":"Blue","releaseDate":"1971-06-22"},
                   "relationships":{"artists":{"data":[{"id":"a1","type":"artists"}]},
                                    "coverArt":{"data":[{"id":"art-77","type":"artworks"}]}}}],
                 "included":[{"id":"a1","type":"artists","attributes":{"name":"Joni Mitchell"}},
                             {"id":"art-77","type":"artworks","attributes":{"files":[
                               {"href":"https://covers/77-320.jpg","meta":{"width":320}}]}}]}""")));

        assertThat(provider.searchTracks("river", 10)).containsExactly(new Track(Provider.TIDAL, "s1", "Joni Mitchell",
                "River", "Blue", Optional.of("https://covers/77-320.jpg")));
    }

    @Test
    void findsASongWithItsAlbum() {
        tidal.stubFor(get(urlPathEqualTo("/v2/searchResults")).withQueryParam("include", equalTo("tracks"))
                .willReturn(okJson(searchResults("tracks", "s2", "s1"))));
        tidal.stubFor(get(urlPathEqualTo("/v2/tracks")).willReturn(okJson("""
                {"data":[
                  {"id":"s2","type":"tracks","attributes":{"title":"River"},
                   "relationships":{"artists":{"data":[{"id":"a9","type":"artists"}]},
                                    "albums":{"data":[{"id":"90","type":"albums"}]}}},
                  {"id":"s1","type":"tracks","attributes":{"title":"River"},
                   "relationships":{"artists":{"data":[{"id":"a1","type":"artists"}]},
                                    "albums":{"data":[{"id":"77","type":"albums"}]}}}],
                 "included":[{"id":"a1","type":"artists","attributes":{"name":"Joni Mitchell"}},
                             {"id":"a9","type":"artists","attributes":{"name":"Leon Bridges"}},
                             {"id":"77","type":"albums","attributes":{"title":"Blue"}}]}""")));
        tidal.stubFor(get(urlPathEqualTo("/v2/albums")).willReturn(okJson(ALBUMS)));

        assertThat(provider.findTrack("Joni Mitchell", "River")).contains(
                new Track(Provider.TIDAL, "s1", "Joni Mitchell", "River", "Blue", Optional.empty()));
        tidal.verify(getRequestedFor(urlPathEqualTo("/v2/tracks"))
                .withQueryParam("include", equalTo("artists,albums")));
    }

    @Test
    void readsTheFormatsTidalOffersEachAlbumAndSongIn() {
        tidal.stubFor(get(urlPathEqualTo("/v2/albums")).willReturn(okJson("""
                {"data":[{"id":"77","type":"albums","attributes":{"title":"Blue",
                   "mediaTags":["LOSSLESS","HIRES_LOSSLESS","DOLBY_ATMOS","SONY_360RA"]},
                   "relationships":{"artists":{"data":[{"id":"a1","type":"artists"}]}}}],
                 "included":[{"id":"a1","type":"artists","attributes":{"name":"Joni Mitchell"}}]}""")));
        tidal.stubFor(get(urlPathEqualTo("/v2/searchResults")).willReturn(okJson(searchResults("tracks", "s1"))));
        tidal.stubFor(get(urlPathEqualTo("/v2/tracks")).willReturn(okJson("""
                {"data":[{"id":"s1","type":"tracks","attributes":{"title":"River","mediaTags":["LOSSLESS"]},
                   "relationships":{"artists":{"data":[{"id":"a1","type":"artists"}]}}}],
                 "included":[{"id":"a1","type":"artists","attributes":{"name":"Joni Mitchell"}}]}""")));

        assertThat(provider.album("77").orElseThrow().formats())
                .containsExactly(AudioFormat.LOSSLESS, AudioFormat.HI_RES, AudioFormat.DOLBY_ATMOS);
        assertThat(provider.searchTracks("river", 1)).singleElement()
                .satisfies(song -> assertThat(song.formats()).containsExactly(AudioFormat.LOSSLESS));
    }

    @Test
    void linksOpenTheAlbumAndPlaylistOnTidal() {
        assertThat(provider.albumLink("77")).isEqualTo(URI.create("https://tidal.com/browse/album/77"));
        assertThat(provider.playlistLink("p1")).isEqualTo(URI.create("https://tidal.com/browse/playlist/p1"));
        assertThat(provider.trackLink("s1")).isEqualTo(URI.create("https://tidal.com/browse/track/s1"));
    }
}
