package io.github.lemonjelly76.musicplayer.adapter.spotify;

import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayBatch;
import io.github.lemonjelly76.musicplayer.domain.port.ListeningHistory;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spotify's recently played tracks: the last 50, each with the playlist it was played from (ADR-010). Polled hourly,
 * so nothing is missed unless someone plays more than 50 tracks in an hour.
 */
class SpotifyListeningHistory implements ListeningHistory {

    private static final String PLAYLIST_URI = "spotify:playlist:";

    private final ProviderAuthorization authorization;
    private final SpotifyApi api;

    SpotifyListeningHistory(ProviderAuthorization authorization, SpotifyApi api) {
        this.authorization = authorization;
        this.api = api;
    }

    @Override
    public String source() {
        return "spotify";
    }

    @Override
    public boolean covers(Person person) {
        return person.provider().equals(Optional.of(Provider.SPOTIFY)) && authorization.registered()
                && authorization.connected(person.id());
    }

    /**
     * The cursor is Spotify's own: the newest play's time in epoch milliseconds.
     */
    @Override
    public PlayBatch playsSince(Person person, Optional<String> cursor) {
        String url = api.url("/me/player/recently-played?limit=50") + cursor.map(after -> "&after=" + after).orElse("");
        JsonNode body = api.get(url, authorization.accessToken(person.id()));
        List<Play> plays = new ArrayList<>();
        for (JsonNode item : body.path("items")) {
            Optional<String> playlist = Optional.of(item.path("context").path("uri").asString(""))
                    .filter(uri -> uri.startsWith(PLAYLIST_URI))
                    .map(uri -> uri.substring(PLAYLIST_URI.length()));
            SpotifyApi.album(item.path("track").path("album")).ifPresent(album -> plays.add(new Play(album,
                    Instant.parse(item.path("played_at").asString()), source(), playlist)));
        }
        Optional<String> next = Optional.of(body.path("cursors").path("after").asString("")).filter(after -> !after.isEmpty());
        return new PlayBatch(plays, next.or(() -> cursor));
    }
}
