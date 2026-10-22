package io.github.lemonjelly76.musicplayer.adapter.tidal;

import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumMatch;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.playlist.TrackMatch;
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
 * the person's.
 */
class TidalMusicProvider implements MusicProvider {

    static final int BATCH = 20;
    static final int COVER_WIDTH = 320;
    private static final int MAX_PAGES = 50;
    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
    private static final String COLLECTED_ALBUMS = "/userCollectionAlbums/me/relationships/items";

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
        return albums(search(query, "albums").stream().limit(limit).toList());
    }

    @Override
    public Optional<AlbumRef> album(String id) {
        return albums(List.of(id)).stream().findFirst();
    }

    @Override
    public Optional<AlbumRef> findAlbum(String artist, String title) {
        return AlbumMatch.best(searchAlbums(artist + " " + title, 10), artist, title);
    }

    /**
     * Songs with their albums' covers, which take one more call for all of them.
     */
    @Override
    public List<Track> searchTracks(String query, int limit) {
        List<FoundTrack> found = searchFoundTracks(query, Math.min(limit, BATCH));
        List<String> albumIds = found.stream().map(FoundTrack::albumId).filter(id -> !id.isEmpty()).distinct().toList();
        Map<String, String> covers = new HashMap<>();
        albums(albumIds).forEach(album -> album.ids().tidal()
                .ifPresent(id -> album.cover().ifPresent(cover -> covers.put(id, cover))));
        return found.stream()
                .map(song -> withCover(song.track(), Optional.ofNullable(covers.get(song.albumId()))))
                .toList();
    }

    @Override
    public Optional<Track> findTrack(String artist, String title) {
        List<FoundTrack> found = searchFoundTracks(artist + " " + title, BATCH);
        return TrackMatch.best(found.stream().map(FoundTrack::track).toList(), artist, title)
                .map(best -> withCover(best, found));
    }

    private List<FoundTrack> searchFoundTracks(String query, int limit) {
        return tracks(search(query, "tracks").stream().limit(limit).toList());
    }

    /**
     * Tidal answers a search with one searchResults resource, whose relationship of the given type lists the ids
     * it found, best first.
     */
    private List<String> search(String query, String type) {
        String path = UriComponentsBuilder.fromPath("/searchResults")
                .queryParam("filter[query]", "{query}")
                .queryParam("countryCode", properties.countryCode())
                .queryParam("include", type)
                .encode()
                .buildAndExpand(query)
                .toUriString();
        JsonNode results = get(path, authorization.appToken()).path("data").path(0);
        return ids(results.path("relationships").path(type));
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
        List<String> ids = allIds(COLLECTED_ALBUMS, token);
        List<AlbumRef> albums = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += BATCH) {
            albums.addAll(albums(ids.subList(start, Math.min(ids.size(), start + BATCH))));
        }
        return albums;
    }

    @Override
    public void addFavourite(PersonId person, String id) {
        send(HttpMethod.POST, COLLECTED_ALBUMS, resources("albums", List.of(id)), authorization.accessToken(person));
    }

    @Override
    public String replacePlaylist(PersonId person, Optional<String> previousPlaylistId, String name,
            String description, List<String> trackIds) {
        String token = authorization.accessToken(person);
        previousPlaylistId.ifPresent(previous -> deletePlaylist(previous, token));
        ObjectNode playlist = jsonMapper.createObjectNode();
        ObjectNode data = playlist.putObject("data").put("type", "playlists");
        data.putObject("attributes").put("name", name).put("description", description).put("accessType", "PUBLIC");
        String playlistId = send(HttpMethod.POST, "/playlists", playlist, token)
                .path("data").path("id").asString();
        for (int start = 0; start < trackIds.size(); start += BATCH) {
            send(HttpMethod.POST, "/playlists/" + playlistId + "/relationships/items",
                    resources("tracks", trackIds.subList(start, Math.min(trackIds.size(), start + BATCH))), token);
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
    public void renamePlaylist(PersonId person, String playlistId, String newName) {
        ObjectNode playlist = jsonMapper.createObjectNode();
        ObjectNode data = playlist.putObject("data").put("id", playlistId).put("type", "playlists");
        data.putObject("attributes").put("name", newName);
        send(HttpMethod.PATCH, "/playlists/" + playlistId, playlist, authorization.accessToken(person));
    }

    @Override
    public URI trackLink(String id) {
        return URI.create(properties.webUrl() + "/track/" + id);
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
     * A song found by search, with the id of the album it is from so its cover can be looked up.
     */
    private record FoundTrack(Track track, String albumId) {
    }

    /**
     * The songs with their artists' names and album titles, in the order asked for; covers are left to
     * {@link #withCover}, as Tidal only gives them with the album.
     */
    private List<FoundTrack> tracks(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/tracks")
                .queryParam("countryCode", properties.countryCode())
                .queryParam("include", "artists,albums");
        ids.forEach(id -> uri.queryParam("filter[id]", id));
        JsonNode body = get(uri.encode().toUriString(), authorization.appToken());
        Map<String, String> artistNames = new HashMap<>();
        Map<String, String> albumTitles = new HashMap<>();
        for (JsonNode included : body.path("included")) {
            String type = included.path("type").asString();
            JsonNode attributes = included.path("attributes");
            if (type.equals("artists")) {
                artistNames.put(included.path("id").asString(), attributes.path("name").asString());
            } else if (type.equals("albums")) {
                albumTitles.put(included.path("id").asString(), attributes.path("title").asString());
            }
        }
        Map<String, FoundTrack> byId = new LinkedHashMap<>();
        for (JsonNode track : body.path("data")) {
            List<String> artists = new ArrayList<>();
            track.path("relationships").path("artists").path("data").forEach(artist ->
                    Optional.ofNullable(artistNames.get(artist.path("id").asString())).ifPresent(artists::add));
            String title = track.path("attributes").path("title").asString("");
            String albumId = track.path("relationships").path("albums").path("data").path(0).path("id").asString("");
            if (!artists.isEmpty() && !title.isBlank()) {
                String id = track.path("id").asString();
                byId.put(id, new FoundTrack(new Track(Provider.TIDAL, id, String.join(", ", artists), title,
                        albumTitles.getOrDefault(albumId, ""), Optional.empty()), albumId));
            }
        }
        return ids.stream().filter(byId::containsKey).map(byId::get).toList();
    }

    private Track withCover(Track track, List<FoundTrack> found) {
        return withCover(track, found.stream().filter(candidate -> candidate.track().equals(track)).findFirst()
                .filter(candidate -> !candidate.albumId().isEmpty())
                .flatMap(candidate -> album(candidate.albumId()))
                .flatMap(AlbumRef::cover));
    }

    private static Track withCover(Track track, Optional<String> cover) {
        return new Track(track.provider(), track.id(), track.artist(), track.title(), track.album(), cover);
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

    /**
     * Tidal says why in {@code errors[0].detail}, such as "Invalid resource ID".
     */
    private String reason(String body) {
        try {
            String detail = jsonMapper.readTree(body).path("errors").path(0).path("detail").asString("");
            return detail.isBlank() ? "" : " (" + detail + ")";
        } catch (JacksonException e) {
            return "";
        }
    }

    private JsonNode call(Supplier<String> request) {
        try {
            String body = request.get();
            return body == null || body.isBlank() ? jsonMapper.createObjectNode() : jsonMapper.readTree(body);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new MusicProviderUnavailableException("Tidal has no such item", e);
            }
            throw new MusicProviderUnavailableException("Tidal refused the request: " + e.getStatusCode()
                    + reason(e.getResponseBodyAsString()), e);
        } catch (RestClientException | JacksonException e) {
            throw new MusicProviderUnavailableException("Could not reach Tidal", e);
        }
    }
}
