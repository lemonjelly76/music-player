package io.github.lemonjelly76.musicplayer.adapter.spotify;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.lemonjelly76.musicplayer.adapter.oauth.InMemoryTokenStore;
import io.github.lemonjelly76.musicplayer.adapter.oauth.OAuthProperties;
import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderTokens;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayBatch;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

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
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SpotifyAdapterTest {

    @RegisterExtension
    static WireMockExtension spotify = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");
    private static final Person CRAIG = Person.create("Craig", Optional.of(Provider.SPOTIFY));

    private final InMemoryTokenStore store = new InMemoryTokenStore();
    private SpotifyMusicProvider provider;
    private SpotifyListeningHistory history;

    @BeforeEach
    void setUp() {
        SpotifyProperties properties = new SpotifyProperties(spotify.baseUrl() + "/v1", "GB", "https://open.spotify.com",
                new OAuthProperties("https://accounts.example/authorize", spotify.baseUrl() + "/token", "client",
                        "secret", "https://home.example/callback", "user-read-recently-played"));
        ProviderAuthorization authorization = new ProviderAuthorization(Provider.SPOTIFY, properties.oauth(),
                RestClient.builder(), store, Clock.fixed(NOW, ZoneOffset.UTC), token -> Optional.empty());
        SpotifyApi api = new SpotifyApi(properties, RestClient.builder());
        provider = new SpotifyMusicProvider(properties, authorization, api);
        history = new SpotifyListeningHistory(authorization, api);
        store.save(CRAIG.id(), Provider.SPOTIFY, new ProviderTokens(Optional.of("craig-sp"), "user-token",
                Optional.empty(), NOW.plusSeconds(3600)));
        spotify.stubFor(post(urlPathEqualTo("/token")).willReturn(okJson("""
                {"access_token":"app-token","expires_in":3600}""")));
    }

    @Test
    void searchesAtMostTenAlbumsAtATime() {
        spotify.stubFor(get(urlPathEqualTo("/v1/search")).willReturn(okJson("""
                {"albums":{"items":[{"id":"sp-blue","name":"Blue","release_date":"1971-06-22",
                  "artists":[{"name":"Joni Mitchell"}]}]}}""")));

        List<AlbumRef> found = provider.searchAlbums("joni", 25);

        assertThat(found).containsExactly(new AlbumRef("Joni Mitchell", "Blue", Optional.of(1971),
                ProviderIds.of(Provider.SPOTIFY, "sp-blue")));
        spotify.verify(getRequestedFor(urlPathEqualTo("/v1/search"))
                .withQueryParam("limit", equalTo("10"))
                .withQueryParam("type", equalTo("album"))
                .withHeader("Authorization", equalTo("Bearer app-token")));
    }

    @Test
    void replacesThePlaylistByUnfollowingTheOldOneAndAddingTheSongs() {
        spotify.stubFor(delete(urlPathEqualTo("/v1/playlists/old/followers")).willReturn(ok()));
        spotify.stubFor(post(urlPathEqualTo("/v1/me/playlists")).willReturn(okJson("""
                {"id":"new-playlist"}""")));
        spotify.stubFor(post(urlPathEqualTo("/v1/playlists/new-playlist/items")).willReturn(okJson("{}")));

        String id = provider.replacePlaylist(CRAIG.id(), Optional.of("old"), "AI Picks", "For Craig", List.of("1", "2"));

        assertThat(id).isEqualTo("new-playlist");
        spotify.verify(deleteRequestedFor(urlPathEqualTo("/v1/playlists/old/followers")));
        spotify.verify(postRequestedFor(urlPathEqualTo("/v1/me/playlists")).withRequestBody(equalToJson("""
                {"name":"AI Picks","description":"For Craig","public":false}""")));
        spotify.verify(postRequestedFor(urlPathEqualTo("/v1/playlists/new-playlist/items")).withRequestBody(equalToJson("""
                {"uris":["spotify:track:1","spotify:track:2"]}""")));
    }

    @Test
    void findsASongOnTheArtistsOwnAlbumRatherThanACompilation() {
        spotify.stubFor(get(urlPathEqualTo("/v1/search")).willReturn(okJson("""
                {"tracks":{"items":[
                  {"id":"tr-5","name":"Sexy Boy","artists":[{"name":"Air"}],
                   "album":{"name":"Pure Chillout","album_type":"compilation","images":[]}},
                  {"id":"tr-9","name":"Sexy Boy - Remastered","artists":[{"name":"Air"}],
                   "album":{"name":"Moon Safari","images":[{"url":"https://i/640","width":640},{"url":"https://i/300","width":300}]}},
                  {"id":"tr-1","name":"Sexy Boy","artists":[{"name":"Air"}],
                   "album":{"name":"Moon Safari","images":[{"url":"https://i/300","width":300}]}}]}}""")));

        Optional<Track> found = provider.findTrack("Air", "Sexy Boy");

        assertThat(found).contains(new Track(Provider.SPOTIFY, "tr-1", "Air", "Sexy Boy", "Moon Safari",
                Optional.of("https://i/300")));
        spotify.verify(getRequestedFor(urlPathEqualTo("/v1/search"))
                .withQueryParam("type", equalTo("track"))
                .withQueryParam("q", equalTo("track:Sexy Boy artist:Air")));
    }

    @Test
    void renamesAPlaylistInThePersonsAccount() {
        spotify.stubFor(put(urlPathEqualTo("/v1/playlists/pl-1")).willReturn(ok()));

        provider.renamePlaylist(CRAIG.id(), "pl-1", "AI Picks — Jogging");

        spotify.verify(putRequestedFor(urlPathEqualTo("/v1/playlists/pl-1"))
                .withHeader("Authorization", equalTo("Bearer user-token"))
                .withRequestBody(equalToJson("""
                        {"name":"AI Picks — Jogging"}""")));
    }

    @Test
    void savesAFavouriteToThePersonsLibrary() {
        spotify.stubFor(put(urlPathEqualTo("/v1/me/library")).willReturn(ok()));

        provider.addFavourite(CRAIG.id(), "sp-blue");

        spotify.verify(putRequestedFor(urlPathEqualTo("/v1/me/library"))
                .withQueryParam("uris", equalTo("spotify:album:sp-blue"))
                .withHeader("Authorization", equalTo("Bearer user-token")));
    }

    @Test
    void waitsAndTriesAgainWhenSpotifyAsksForABreather() {
        spotify.stubFor(put(urlPathEqualTo("/v1/me/library")).inScenario("busy").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(jsonResponse("""
                        {"error":{"status":429,"message":"Too many requests"}}""", 429).withHeader("Retry-After", "0"))
                .willSetStateTo("calm"));
        spotify.stubFor(put(urlPathEqualTo("/v1/me/library")).inScenario("busy").whenScenarioStateIs("calm")
                .willReturn(ok()));

        provider.addFavourite(CRAIG.id(), "sp-blue");

        spotify.verify(2, putRequestedFor(urlPathEqualTo("/v1/me/library")));
    }

    @Test
    void givesUpWhenSpotifyAsksForALongWait() {
        spotify.stubFor(put(urlPathEqualTo("/v1/me/library")).willReturn(jsonResponse("""
                {"error":{"status":429,"message":"Too many requests"}}""", 429).withHeader("Retry-After", "3600")));

        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> provider.addFavourite(CRAIG.id(), "sp-blue"))
                .withMessageContaining("Too many requests").withMessageContaining("try again in 60 minutes");
        spotify.verify(1, putRequestedFor(urlPathEqualTo("/v1/me/library")));
    }

    @Test
    void saysWhatSpotifyRefusedAndWhy() {
        spotify.stubFor(put(urlPathEqualTo("/v1/me/library")).willReturn(jsonResponse("""
                {"error":{"status":403,"message":"Insufficient client scope"}}""", 403)));

        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> provider.addFavourite(CRAIG.id(), "sp-blue"))
                .withMessageContaining("Insufficient client scope");
    }

    @Test
    void readsRecentPlaysWithThePlaylistTheyCameFromAndTheNextCursor() {
        spotify.stubFor(get(urlPathEqualTo("/v1/me/player/recently-played")).willReturn(okJson("""
                {"items":[
                  {"track":{"album":{"id":"sp-blue","name":"Blue","artists":[{"name":"Joni Mitchell"}]}},
                   "played_at":"2026-10-20T17:00:00.000Z","context":{"type":"playlist","uri":"spotify:playlist:pl-1"}},
                  {"track":{"album":{"id":"sp-blue","name":"Blue","artists":[{"name":"Joni Mitchell"}]}},
                   "played_at":"2026-10-20T16:55:00.000Z","context":{"type":"album","uri":"spotify:album:sp-blue"}}],
                 "cursors":{"after":"1792515600000"}}""")));

        PlayBatch batch = history.playsSince(CRAIG, Optional.of("1792500000000"));

        assertThat(batch.plays()).extracting(Play::playlistId).containsExactly(Optional.of("pl-1"), Optional.empty());
        assertThat(batch.plays().getFirst().playedAt()).isEqualTo(Instant.parse("2026-10-20T17:00:00Z"));
        assertThat(batch.nextCursor()).contains("1792515600000");
        spotify.verify(getRequestedFor(urlPathEqualTo("/v1/me/player/recently-played"))
                .withQueryParam("after", equalTo("1792500000000"))
                .withHeader("Authorization", equalTo("Bearer user-token")));
    }

    @Test
    void keepsTheCursorWhenNothingNewWasPlayed() {
        spotify.stubFor(get(urlPathEqualTo("/v1/me/player/recently-played")).willReturn(okJson("""
                {"items":[],"cursors":null}""")));

        assertThat(history.playsSince(CRAIG, Optional.of("5")).nextCursor()).contains("5");
    }

    @Test
    void onlyCoversConnectedSpotifyUsers() {
        assertThat(history.covers(CRAIG)).isTrue();
        assertThat(history.covers(Person.create("Sam", Optional.of(Provider.TIDAL)))).isFalse();
    }
}
