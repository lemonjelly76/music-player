package io.github.lemonjelly76.musicplayer.adapter.lastfm;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayBatch;
import io.github.lemonjelly76.musicplayer.domain.port.ListeningHistory;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A person's Last.fm scrobbles, for Tidal and anything else that scrobbles (ADR-010). Scrobbles do not say which
 * playlist a track came from, so these plays count towards the person, not a category.
 */
class LastFmListeningHistory implements ListeningHistory {

    static final int PAGE_SIZE = 200;
    static final int MAX_PAGES = 5;

    private final LastFmApi api;

    LastFmListeningHistory(LastFmApi api) {
        this.api = api;
    }

    @Override
    public String source() {
        return "lastfm";
    }

    @Override
    public boolean covers(Person person) {
        return api.configured() && person.lastFmUser().isPresent();
    }

    /**
     * The cursor is the newest scrobble's time in epoch seconds; Last.fm's {@code from} includes that second, so
     * the next read starts one second on.
     */
    @Override
    public PlayBatch playsSince(Person person, Optional<String> cursor) {
        List<Play> plays = new ArrayList<>();
        long newest = cursor.map(Long::parseLong).orElse(0L);
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, String> params = new HashMap<>(Map.of(
                    "user", person.lastFmUser().orElseThrow(),
                    "limit", String.valueOf(PAGE_SIZE),
                    "page", String.valueOf(page)));
            cursor.ifPresent(from -> params.put("from", String.valueOf(Long.parseLong(from) + 1)));
            Optional<JsonNode> body = api.call("user.getrecenttracks", params, false);
            if (body.isEmpty()) {
                throw new IllegalStateException("Last.fm did not answer for " + person.lastFmUser().orElseThrow());
            }
            JsonNode tracks = body.get().path("recenttracks");
            for (JsonNode track : tracks.path("track")) {
                Optional<Play> play = play(track);
                play.ifPresent(plays::add);
            }
            if (page >= tracks.path("@attr").path("totalPages").asInt(1)) {
                break;
            }
        }
        for (Play play : plays) {
            newest = Math.max(newest, play.playedAt().getEpochSecond());
        }
        return new PlayBatch(plays, newest == 0 ? cursor : Optional.of(String.valueOf(newest)));
    }

    private Optional<Play> play(JsonNode track) {
        if (track.path("@attr").path("nowplaying").asString("").equals("true")) {
            return Optional.empty();
        }
        String artist = track.path("artist").path("#text").asString("").strip();
        String album = track.path("album").path("#text").asString("").strip();
        long uts = track.path("date").path("uts").asLong(0);
        if (artist.isEmpty() || album.isEmpty() || uts == 0) {
            return Optional.empty();
        }
        return Optional.of(new Play(AlbumRef.of(artist, album), Instant.ofEpochSecond(uts), source(), Optional.empty()));
    }
}
