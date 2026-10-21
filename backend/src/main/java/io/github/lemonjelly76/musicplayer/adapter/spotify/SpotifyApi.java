package io.github.lemonjelly76.musicplayer.adapter.spotify;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The raw Spotify calls, shared by the provider and the listening history.
 */
class SpotifyApi {

    private static final int MAX_PAGES = 40;
    static final int COVER_WIDTH = 250;

    private final SpotifyProperties properties;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    SpotifyApi(SpotifyProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    String url(String path) {
        return properties.apiUrl() + path;
    }

    JsonNode get(String url, String token) {
        return send(HttpMethod.GET, url, null, token);
    }

    JsonNode send(HttpMethod method, String url, Object body, String token) {
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri(URI.create(url))
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(token));
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(jsonMapper.writeValueAsString(body));
            }
            String response = request.retrieve().body(String.class);
            return response == null || response.isBlank() ? jsonMapper.createObjectNode() : jsonMapper.readTree(response);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new MusicProviderUnavailableException("Spotify has no such item", e);
            }
            throw new MusicProviderUnavailableException("Spotify refused the request: " + e.getStatusCode()
                    + reason(e.getResponseBodyAsString()), e);
        } catch (RestClientException | JacksonException e) {
            throw new MusicProviderUnavailableException("Could not reach Spotify", e);
        }
    }

    /**
     * Every item of a paged list, following Spotify's absolute {@code next} links.
     */
    List<JsonNode> allItems(String firstPage, String token) {
        List<JsonNode> items = new ArrayList<>();
        String page = firstPage;
        for (int pages = 0; page != null && pages < MAX_PAGES; pages++) {
            JsonNode body = get(page, token);
            body.path("items").forEach(items::add);
            String next = body.path("next").asString("");
            page = next.isEmpty() ? null : next;
        }
        return items;
    }

    /**
     * Spotify says why in {@code error.message}, such as "Insufficient client scope".
     */
    private String reason(String body) {
        try {
            String message = jsonMapper.readTree(body).path("error").path("message").asString("");
            return message.isBlank() ? "" : " (" + message + ")";
        } catch (JacksonException e) {
            return "";
        }
    }

    static Optional<AlbumRef> album(JsonNode album) {
        List<String> artists = new ArrayList<>();
        album.path("artists").forEach(artist -> artists.add(artist.path("name").asString()));
        String title = album.path("name").asString("");
        String id = album.path("id").asString("");
        if (artists.isEmpty() || title.isBlank() || id.isEmpty()) {
            return Optional.empty();
        }
        String releaseDate = album.path("release_date").asString("");
        Optional<Integer> year = releaseDate.length() >= 4 ? Optional.of(Integer.parseInt(releaseDate.substring(0, 4)))
                : Optional.empty();
        return Optional.of(new AlbumRef(String.join(", ", artists), title, year, ProviderIds.of(Provider.SPOTIFY, id),
                cover(album.path("images"))));
    }

    /**
     * Spotify lists each cover at 640, 300 and 64 pixels; the smallest at least 250 wide suits a phone card.
     */
    private static Optional<String> cover(JsonNode images) {
        String best = null;
        int bestWidth = Integer.MAX_VALUE;
        for (JsonNode image : images) {
            int width = image.path("width").asInt(0);
            if (best == null || (width >= COVER_WIDTH && width < bestWidth)) {
                best = image.path("url").asString(null);
                bestWidth = width >= COVER_WIDTH ? width : bestWidth;
            }
        }
        return Optional.ofNullable(best);
    }
}
