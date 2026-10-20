package io.github.lemonjelly76.musicplayer.adapter.lidarr;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumMatch;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.port.AlbumWishlist;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adds albums to Lidarr as wanted, through its v1 API (ADR-012). An album Lidarr already lists is set to monitored
 * and searched for; a new one is added with its artist, who is otherwise left unmonitored.
 */
class LidarrWishlist implements AlbumWishlist {

    private final LidarrProperties properties;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    LidarrWishlist(LidarrProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public String want(AlbumRef album) {
        String term = album.ids().musicBrainz().map(mbid -> "lidarr:" + mbid)
                .orElse(album.artist() + " " + album.title());
        JsonNode results = call(HttpMethod.GET, UriComponentsBuilder.fromPath("/album/lookup").queryParam("term", term)
                .encode().toUriString(), null);
        List<AlbumRef> names = new ArrayList<>();
        List<JsonNode> found = new ArrayList<>();
        for (JsonNode result : results) {
            names.add(AlbumRef.of(result.path("artist").path("artistName").asString("?"), result.path("title").asString("?")));
            found.add(result);
        }
        JsonNode match = album.ids().musicBrainz().isPresent() && !found.isEmpty()
                ? found.getFirst()
                : AlbumMatch.best(names, album.artist(), album.title()).map(name -> found.get(names.indexOf(name)))
                        .orElseThrow(() -> new IllegalArgumentException("Lidarr cannot find " + album.label()));
        int existingId = match.path("id").asInt(0);
        if (existingId > 0) {
            call(HttpMethod.PUT, "/album/monitor", Map.of("albumIds", List.of(existingId), "monitored", true));
            call(HttpMethod.POST, "/command", Map.of("name", "AlbumSearch", "albumIds", List.of(existingId)));
            return album.label() + " was already in Lidarr; it is now monitored and being searched for";
        }
        ObjectNode added = ((ObjectNode) match).put("monitored", true);
        added.putObject("addOptions").put("searchForNewAlbum", true);
        ObjectNode artist = (ObjectNode) added.path("artist");
        if (artist.path("id").asInt(0) == 0) {
            artist.put("qualityProfileId", properties.qualityProfileId())
                    .put("metadataProfileId", properties.metadataProfileId())
                    .put("rootFolderPath", properties.rootFolder())
                    .put("monitored", true)
                    .put("monitorNewItems", "none");
            artist.putObject("addOptions").put("monitor", "none").put("searchForMissingAlbums", false);
        }
        call(HttpMethod.POST, "/album", added);
        return album.label() + " was added to Lidarr as wanted and is being searched for";
    }

    private JsonNode call(HttpMethod method, String path, Object body) {
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri(URI.create(properties.url() + "/api/v1" + path))
                    .header("X-Api-Key", properties.apiKey())
                    .accept(MediaType.APPLICATION_JSON);
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(jsonMapper.writeValueAsString(body));
            }
            String response = request.retrieve().body(String.class);
            return response == null || response.isBlank() ? jsonMapper.createArrayNode() : jsonMapper.readTree(response);
        } catch (RestClientException | JacksonException e) {
            throw new MusicProviderUnavailableException("Could not reach Lidarr: " + e.getMessage(), e);
        }
    }
}
