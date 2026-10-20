package io.github.lemonjelly76.musicplayer.adapter.musicbrainz;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumMatch;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.port.AlbumCatalogue;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MusicBrainz release groups: the neutral check that an album exists, and the id Lidarr uses (ADR-009).
 * Unreachable counts as unknown, so the pick is dropped rather than trusted.
 */
class MusicBrainzCatalogue implements AlbumCatalogue {

    private static final System.Logger LOG = System.getLogger(MusicBrainzCatalogue.class.getName());
    private static final long MIN_GAP_MILLIS = 1100;

    private final MusicBrainzProperties properties;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private long lastCall;

    MusicBrainzCatalogue(MusicBrainzProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public Optional<AlbumRef> findAlbum(String artist, String title) {
        String query = "releasegroup:\"" + escape(title) + "\" AND artist:\"" + escape(artist) + "\"";
        String url = UriComponentsBuilder.fromUriString(properties.apiUrl() + "/release-group")
                .queryParam("query", query)
                .queryParam("fmt", "json")
                .queryParam("limit", 10)
                .encode()
                .toUriString();
        List<AlbumRef> found = new ArrayList<>();
        get(url).ifPresent(body -> body.path("release-groups")
                .forEach(group -> album(group).ifPresent(found::add)));
        return AlbumMatch.best(found, artist, title);
    }

    @Override
    public Optional<AlbumRef> byMusicBrainzId(String mbid) {
        return get(properties.apiUrl() + "/release-group/" + mbid + "?inc=artist-credits&fmt=json")
                .flatMap(MusicBrainzCatalogue::album);
    }

    private static Optional<AlbumRef> album(JsonNode group) {
        StringBuilder artist = new StringBuilder();
        group.path("artist-credit").forEach(credit -> artist.append(credit.path("name").asString(""))
                .append(credit.path("joinphrase").asString("")));
        String title = group.path("title").asString("");
        String id = group.path("id").asString("");
        if (artist.isEmpty() || title.isBlank() || id.isEmpty()) {
            return Optional.empty();
        }
        String date = group.path("first-release-date").asString("");
        Optional<Integer> year = date.length() >= 4 ? Optional.of(Integer.parseInt(date.substring(0, 4)))
                : Optional.empty();
        return Optional.of(new AlbumRef(artist.toString(), title, year, ProviderIds.musicBrainz(id)));
    }

    /**
     * MusicBrainz answers 503 when callers go too fast, so a busy answer is tried once more after a pause.
     */
    private Optional<JsonNode> get(String url) {
        for (int attempt = 1; ; attempt++) {
            waitForTurn();
            try {
                String body = restClient.get()
                        .uri(URI.create(url))
                        .header("User-Agent", properties.userAgent())
                        .retrieve()
                        .body(String.class);
                return Optional.of(jsonMapper.readTree(body));
            } catch (HttpServerErrorException.ServiceUnavailable e) {
                if (attempt == 2) {
                    LOG.log(System.Logger.Level.WARNING, "MusicBrainz is busy; treating " + url + " as unknown");
                    return Optional.empty();
                }
            } catch (HttpClientErrorException e) {
                return Optional.empty();
            } catch (RestClientException | JacksonException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not reach MusicBrainz for " + url, e);
                return Optional.empty();
            }
        }
    }

    private synchronized void waitForTurn() {
        long wait = lastCall + MIN_GAP_MILLIS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCall = System.currentTimeMillis();
    }

    private static String escape(String text) {
        return text.replaceAll("([+\\-!(){}\\[\\]^\"~*?:\\\\/]|&&|\\|\\|)", "\\\\$1");
    }
}
