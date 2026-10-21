package io.github.lemonjelly76.musicplayer.adapter.tidal;

import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumMatch;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Tidal through its JSON:API v2 (ADR-006). Catalogue reads use the app's own token; favourites and playlists use
 * the person's. Tidal playlists hold tracks, so an album is written as all of its tracks in order.
 */
class TidalMusicProvider implements MusicProvider {

    static final int BATCH = 20;
    static final int COVER_WIDTH = 320;
    private static final int MAX_PAGES = 50;
    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");

    private final TidalProperties properties;
    private final ProviderAuthorization authorization;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    TidalMusicProvider(TidalProperties properties, ProviderAuthorization authorization,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.authorization = authorization;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public Provider name() {
        return Provider.TIDAL;
    }

    @Override
    public boolean configured() {
        return authorization.registered();
    }

    @Override
    public boolean connected(PersonId person) {
        return authorization.connected(person);
    }

    @Override
    public List<AlbumRef> searchAlbums(String query, int limit) {
        String path = UriComponentsBuilder.fromPath("/searchResults/{query}/relationships/albums")
                .queryParam("countryCode", properties.countryCode())
                .buildAndExpand(query)
                .encode()
                .toUriString();
        List<String> ids = ids(get(path, authorization.appToken())).stream().limit(limit).toList();
        return albums(ids);
    }

    @Override
    public Optional<AlbumRef> album(String id) {
        return albums(List.of(id)).stream().findFirst();
    }

    @Override
    public Optional<AlbumRef> findAlbum(String artist, String title) {
        return AlbumMatch.best(searchAlbums(artist + " " + title, 10), artist, title);
    }

    @Override
    public List<AlbumRef> similarAlbums(String id, int limit) {
        JsonNode body = get("/albums/" + id + "/relationships/similarAlbums?countryCode=" + properties.countryCode(),
                authorization.appToken());
        return albums(ids(body).stream().limit(limit).toList());
    }

    @Override
    public List<AlbumRef> favouriteAlbums(PersonId person) {
        String token = authorization.accessToken(person);
        List<String> ids = allIds("/userCollections/" + userId(person) + "/relationships/albums?countryCode="
                + properties.countryCode(), token);
        List<AlbumRef> albums = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += BATCH) {
            albums.addAll(albums(ids.subList(start, Math.min(ids.size(), start + BATCH))));
        }
        return albums;
    }

    @Override
    public void addFavourite(PersonId person, String id) {
        send(HttpMethod.POST, "/userCollections/" + userId(person) + "/relationships/albums?countryCode="
                + properties.countryCode(), resources("albums", List.of(id)), authorization.accessToken(person));
    }

    @Override
    public String replacePlaylist(PersonId person, Optional<String> previousPlaylistId, String name,
            String description, List<String> albumIds) {
        String token = authorization.accessToken(person);
        previousPlaylistId.ifPresent(previous -> deletePlaylist(previous, token));
        ObjectNode playlist = jsonMapper.createObjectNode();
        ObjectNode data = playlist.putObject("data").put("type", "playlists");
        data.putObject("attributes").put("name", name).put("description", description).put("accessType", "PUBLIC");
        String playlistId = send(HttpMethod.POST, "/playlists?countryCode=" + properties.countryCode(), playlist, token)
                .path("data").path("id").asString();
        List<String> tracks = new ArrayList<>();
        for (String albumId : albumIds) {
            tracks.addAll(allIds("/albums/" + albumId + "/relationships/items?countryCode=" + properties.countryCode(),
                    authorization.appToken()));
        }
        for (int start = 0; start < tracks.size(); start += BATCH) {
            send(HttpMethod.POST, "/playlists/" + playlistId + "/relationships/items",
                    resources("tracks", tracks.subList(start, Math.min(tracks.size(), start + BATCH))), token);
        }
        return playlistId;
    }

    @Override
    public URI albumLink(String id) {
        return URI.create(properties.webUrl() + "/album/" + id);
    }

    @Override
    public URI searchLink(String query) {
        return URI.create(properties.webUrl() + "/search?q=" + UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8));
    }

    @Override
    public URI playlistLink(String playlistId) {
        return URI.create(properties.webUrl() + "/playlist/" + playlistId);
    }

    /**
     * The signed-in person's Tidal user id, read once after they connect.
     */
    static Optional<String> currentUserId(TidalProperties properties, RestClient.Builder restClientBuilder,
            String accessToken) {
        try {
            String body = restClientBuilder.build().get()
                    .uri(URI.create(properties.apiUrl() + "/users/me"))
                    .accept(JSON_API)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(String.class);
            return Optional.of(JsonMapper.builder().build().readTree(body).path("data").path("id").asString(""))
                    .filter(id -> !id.isEmpty());
        } catch (RestClientException | JacksonException e) {
            return Optional.empty();
        }
    }

    private String userId(PersonId person) {
        return authorization.userId(person).orElseThrow(() -> new MusicProviderUnavailableException(
                "Tidal did not say who this person is; connect Tidal again from the app"));
    }

    private void deletePlaylist(String playlistId, String token) {
        try {
            send(HttpMethod.DELETE, "/playlists/" + playlistId, null, token);
        } catch (MusicProviderUnavailableException e) {
            if (!(e.getCause() instanceof HttpClientErrorException.NotFound)) {
                throw e;
            }
        }
    }

    /**
     * The albums with their artists' names, in the order asked for; ids Tidal does not know are left out.
     */
    private List<AlbumRef> albums(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/albums")
                .queryParam("countryCode", properties.countryCode())
                .queryParam("include", "artists,coverArt");
        ids.forEach(id -> uri.queryParam("filter[id]", id));
        JsonNode body = get(uri.encode().toUriString(), authorization.appToken());
        Map<String, String> artistNames = new HashMap<>();
        Map<String, String> covers = new HashMap<>();
        for (JsonNode included : body.path("included")) {
            String type = included.path("type").asString();
            if (type.equals("artists")) {
                artistNames.put(included.path("id").asString(), included.path("attributes").path("name").asString());
            } else if (type.equals("artworks")) {
                coverFile(included).ifPresent(url -> covers.put(included.path("id").asString(), url));
            }
        }
        Map<String, AlbumRef> byId = new LinkedHashMap<>();
        for (JsonNode album : body.path("data")) {
            toAlbum(album, artistNames, covers).ifPresent(found -> byId.put(album.path("id").asString(), found));
        }
        return ids.stream().filter(byId::containsKey).map(byId::get).toList();
    }

    /**
     * Tidal offers each artwork in several sizes; the smallest at least 320 wide suits a phone card.
     */
    private static Optional<String> coverFile(JsonNode artwork) {
        String best = null;
        int bestWidth = Integer.MAX_VALUE;
        for (JsonNode file : artwork.path("attributes").path("files")) {
            int width = file.path("meta").path("width").asInt(0);
            if (best == null || (width >= COVER_WIDTH && width < bestWidth)) {
                best = file.path("href").asString(null);
                bestWidth = width >= COVER_WIDTH ? width : bestWidth;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<AlbumRef> toAlbum(JsonNode album, Map<String, String> artistNames,
            Map<String, String> covers) {
        List<String> artists = new ArrayList<>();
        album.path("relationships").path("artists").path("data")
                .forEach(artist -> Optional.ofNullable(artistNames.get(artist.path("id").asString())).ifPresent(artists::add));
        String title = album.path("attributes").path("title").asString("");
        if (artists.isEmpty() || title.isBlank()) {
            return Optional.empty();
        }
        String releaseDate = album.path("attributes").path("releaseDate").asString("");
        Optional<Integer> year = releaseDate.length() >= 4 ? Optional.of(Integer.parseInt(releaseDate.substring(0, 4)))
                : Optional.empty();
        Optional<String> cover = Optional.ofNullable(covers.get(
                album.path("relationships").path("coverArt").path("data").path(0).path("id").asString("")));
        return Optional.of(new AlbumRef(String.join(", ", artists), title, year,
                ProviderIds.of(Provider.TIDAL, album.path("id").asString()), cover));
    }

    private static List<String> ids(JsonNode body) {
        List<String> ids = new ArrayList<>();
        body.path("data").forEach(resource -> ids.add(resource.path("id").asString()));
        return ids;
    }

    /**
     * Every id in a relationship, following Tidal's {@code links.next} cursor.
     */
    private List<String> allIds(String firstPage, String token) {
        List<String> ids = new ArrayList<>();
        String page = firstPage;
        for (int pages = 0; page != null && pages < MAX_PAGES; pages++) {
            JsonNode body = get(page, token);
            ids.addAll(ids(body));
            String next = body.path("links").path("next").asString("");
            page = next.isEmpty() ? null : relative(next);
        }
        return ids;
    }

    /**
     * Tidal's links start from the API's root, which the base URL already includes.
     */
    private String relative(String link) {
        String path = link.startsWith("http") ? URI.create(link).getRawPath() + "?" + URI.create(link).getRawQuery()
                : link;
        String root = URI.create(properties.apiUrl()).getPath();
        return !root.isEmpty() && path.startsWith(root + "/") ? path.substring(root.length()) : path;
    }

    private ObjectNode resources(String type, List<String> ids) {
        ObjectNode body = jsonMapper.createObjectNode();
        ArrayNode data = body.putArray("data");
        ids.forEach(id -> data.addObject().put("id", id).put("type", type));
        return body;
    }

    private JsonNode get(String path, String token) {
        return call(() -> restClient.get()
                .uri(URI.create(properties.apiUrl() + path))
                .accept(JSON_API)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(String.class));
    }

    private JsonNode send(HttpMethod method, String path, JsonNode body, String token) {
        return call(() -> {
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri(URI.create(properties.apiUrl() + path))
                    .accept(JSON_API)
                    .headers(headers -> headers.setBearerAuth(token));
            if (body != null) {
                request.contentType(JSON_API).body(body.toString());
            }
            return request.retrieve().body(String.class);
        });
    }

    private JsonNode call(Supplier<String> request) {
        try {
            String body = request.get();
            return body == null || body.isBlank() ? jsonMapper.createObjectNode() : jsonMapper.readTree(body);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new MusicProviderUnavailableException("Tidal has no such item", e);
            }
            throw new MusicProviderUnavailableException("Tidal refused the request: " + e.getStatusCode(), e);
        } catch (RestClientException | JacksonException e) {
            throw new MusicProviderUnavailableException("Could not reach Tidal", e);
        }
    }
}
