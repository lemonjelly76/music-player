package io.github.lemonjelly76.musicplayer.adapter.lastfm;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class LastFmAdapterTest {

    @RegisterExtension
    static WireMockExtension lastFm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final LastFmApi api = new LastFmApi(new LastFmProperties(lastFm.baseUrl() + "/2.0/", "key-1", "test/1.0"),
            RestClient.builder(), Clock.fixed(Instant.parse("2026-10-20T18:00:00Z"), ZoneOffset.UTC));

    private static String topAlbums(String artist, String... titles) {
        StringBuilder albums = new StringBuilder();
        for (String title : titles) {
            albums.append(albums.isEmpty() ? "" : ",")
                    .append("{\"name\":\"").append(title).append("\",\"artist\":{\"name\":\"").append(artist).append("\"}}");
        }
        return "{\"topalbums\":{\"album\":[" + albums + "]}}";
    }

    @Test
    void suggestsTheArtistsOtherAlbumsAndTheTopAlbumsOfSimilarArtists() {
        lastFm.stubFor(get(urlPathEqualTo("/2.0/")).withQueryParam("method", equalTo("artist.gettopalbums"))
                .withQueryParam("artist", equalTo("Radiohead"))
                .willReturn(okJson(topAlbums("Radiohead", "OK Computer", "Kid A", "(null)", "In Rainbows"))));
        lastFm.stubFor(get(urlPathEqualTo("/2.0/")).withQueryParam("method", equalTo("artist.getsimilar"))
                .willReturn(okJson("""
                        {"similarartists":{"artist":[{"name":"Thom Yorke"},{"name":"Portishead"}]}}""")));
        lastFm.stubFor(get(urlPathEqualTo("/2.0/")).withQueryParam("method", equalTo("artist.gettopalbums"))
                .withQueryParam("artist", equalTo("Thom Yorke"))
                .willReturn(okJson(topAlbums("Thom Yorke", "The Eraser"))));
        lastFm.stubFor(get(urlPathEqualTo("/2.0/")).withQueryParam("method", equalTo("artist.gettopalbums"))
                .withQueryParam("artist", equalTo("Portishead"))
                .willReturn(okJson(topAlbums("Portishead", "Dummy", "Third", "Portishead"))));
        LastFmSimilarity similarity = new LastFmSimilarity(api);

        assertThat(similarity.similarTo(AlbumRef.of("Radiohead", "OK Computer"), 20))
                .extracting(AlbumRef::label)
                .containsExactly("Radiohead — Kid A", "Radiohead — In Rainbows", "Thom Yorke — The Eraser",
                        "Portishead — Dummy", "Portishead — Third");

        similarity.similarTo(AlbumRef.of("Radiohead", "OK Computer"), 20);
        lastFm.verify(1, getRequestedFor(urlPathEqualTo("/2.0/")).withQueryParam("method", equalTo("artist.getsimilar")));
        lastFm.verify(getRequestedFor(urlPathEqualTo("/2.0/")).withQueryParam("api_key", equalTo("key-1")));
    }

    @Test
    void suggestsNothingWhenLastFmFails() {
        lastFm.stubFor(get(urlPathEqualTo("/2.0/")).willReturn(okJson("""
                {"error":29,"message":"Rate limit exceeded"}""")));

        assertThat(new LastFmSimilarity(api).similarTo(AlbumRef.of("Radiohead", "OK Computer"), 20)).isEmpty();
    }

    @Test
    void readsScrobblesSinceTheCursorSkippingWhatIsPlayingNow() {
        lastFm.stubFor(get(urlPathEqualTo("/2.0/")).withQueryParam("method", equalTo("user.getrecenttracks"))
                .willReturn(okJson("""
                        {"recenttracks":{"track":[
                          {"artist":{"#text":"Air"},"album":{"#text":"Moon Safari"},"@attr":{"nowplaying":"true"}},
                          {"artist":{"#text":"Air"},"album":{"#text":"Moon Safari"},"date":{"uts":"1792515600"}},
                          {"artist":{"#text":"Air"},"album":{"#text":""},"date":{"uts":"1792515000"}},
                          {"artist":{"#text":"Air"},"album":{"#text":"Talkie Walkie"},"date":{"uts":"1792514000"}}],
                         "@attr":{"totalPages":"1"}}}""")));
        Person craig = Person.create("Craig", Optional.empty()).withLastFmUser(Optional.of("craigfm"));
        LastFmListeningHistory history = new LastFmListeningHistory(api);

        PlayBatch batch = history.playsSince(craig, Optional.of("1792500000"));

        assertThat(batch.plays()).extracting(play -> play.album().title(), Play::playlistId)
                .containsExactly(tuple("Moon Safari", Optional.empty()),
                        tuple("Talkie Walkie", Optional.empty()));
        assertThat(batch.nextCursor()).contains("1792515600");
        lastFm.verify(getRequestedFor(urlPathEqualTo("/2.0/"))
                .withQueryParam("user", equalTo("craigfm"))
                .withQueryParam("from", equalTo("1792500001")));
        assertThat(history.covers(craig)).isTrue();
        assertThat(history.covers(Person.create("Sam", Optional.empty()))).isFalse();
    }
}
