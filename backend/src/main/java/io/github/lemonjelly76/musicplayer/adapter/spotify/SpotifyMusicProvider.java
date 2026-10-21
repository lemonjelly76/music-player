package io.github.lemonjelly76.musicplayer.adapter.spotify;

import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumMatch;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spotify as the fallback service (ADR-006). Newer apps are refused the older write endpoints
 * ({@code /users/{id}/playlists}, {@code /playlists/{id}/tracks}, {@code /me/albums}), so playlists are created under
 * {@code /me}, filled through {@code /items} and favourites saved through {@code /me/library}. It offers no recommendations to new apps, so it adds nothing to the
 * candidate pool; the model and the other sources do that.
 */
class SpotifyMusicProvider implements MusicProvider {

    /**
     * Development Mode caps a search page at ten results.
     */
    static final int SEARCH_LIMIT = 10;
    static final int TRACKS_PER_ADD = 100;

    private final SpotifyProperties properties;
    private final ProviderAuthorization authorization;
    private final SpotifyApi api;

    SpotifyMusicProvider(SpotifyProperties properties, ProviderAuthorization authorization, SpotifyApi api) {
        this.properties = properties;
        this.authorization = authorization;
        this.api = api;
    }

    @Override
    public Provider name() {
        return Provider.SPOTIFY;
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
        String url = UriComponentsBuilder.fromUriString(api.url("/search"))
                .queryParam("q", query)
                .queryParam("type", "album")
                .queryParam("market", properties.market())
                .queryParam("limit", Math.min(limit, SEARCH_LIMIT))
                .encode()
                .toUriString();
        List<AlbumRef> albums = new ArrayList<>();
        api.get(url, authorization.appToken()).path("albums").path("items")
                .forEach(album -> SpotifyApi.album(album).ifPresent(albums::add));
        return albums;
    }

    @Override
    public Optional<AlbumRef> album(String id) {
        try {
            return SpotifyApi.album(api.get(api.url("/albums/" + id + "?market=" + properties.market()),
                    authorization.appToken()));
        } catch (MusicProviderUnavailableException e) {
            if (e.getCause() instanceof HttpClientErrorException.NotFound
                    || e.getCause() instanceof HttpClientErrorException.BadRequest) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Optional<AlbumRef> findAlbum(String artist, String title) {
        List<AlbumRef> found = searchAlbums("album:" + title + " artist:" + artist, SEARCH_LIMIT);
        return AlbumMatch.best(found, artist, title)
                .or(() -> AlbumMatch.best(searchAlbums(artist + " " + title, SEARCH_LIMIT), artist, title));
    }

    @Override
    public List<AlbumRef> similarAlbums(String id, int limit) {
        return List.of();
    }

    @Override
    public List<AlbumRef> favouriteAlbums(PersonId person) {
        List<AlbumRef> albums = new ArrayList<>();
        api.allItems(api.url("/me/albums?limit=50&market=" + properties.market()), authorization.accessToken(person))
                .forEach(item -> SpotifyApi.album(item.path("album")).ifPresent(albums::add));
        return albums;
    }

    /**
     * Spotify refuses the older {@code /me/albums} for newer apps; the general library endpoint takes any item.
     */
    @Override
    public void addFavourite(PersonId person, String id) {
        api.send(HttpMethod.PUT, api.url("/me/library?uris=spotify:album:" + id), null, authorization.accessToken(person));
    }

    @Override
    public String replacePlaylist(PersonId person, Optional<String> previousPlaylistId, String name,
            String description, List<String> albumIds) {
        String token = authorization.accessToken(person);
        previousPlaylistId.ifPresent(previous -> unfollow(previous, token));
        String playlistId = api.send(HttpMethod.POST, api.url("/me/playlists"),
                Map.of("name", name, "description", description, "public", false), token).path("id").asString();
        List<String> uris = new ArrayList<>();
        for (String albumId : albumIds) {
            api.allItems(api.url("/albums/" + albumId + "/tracks?limit=50"), authorization.appToken())
                    .forEach(track -> uris.add(track.path("uri").asString()));
        }
        for (int start = 0; start < uris.size(); start += TRACKS_PER_ADD) {
            api.send(HttpMethod.POST, api.url("/playlists/" + playlistId + "/items"),
                    Map.of("uris", uris.subList(start, Math.min(uris.size(), start + TRACKS_PER_ADD))), token);
        }
        return playlistId;
    }

    @Override
    public URI albumLink(String id) {
        return URI.create(properties.webUrl() + "/album/" + id);
    }

    @Override
    public URI searchLink(String query) {
        return URI.create(properties.webUrl() + "/search/" + UriUtils.encodePathSegment(query, StandardCharsets.UTF_8));
    }

    @Override
    public URI playlistLink(String playlistId) {
        return URI.create(properties.webUrl() + "/playlist/" + playlistId);
    }

    /**
     * Spotify has no playlist delete; unfollowing your own playlist removes it from your library.
     */
    private void unfollow(String playlistId, String token) {
        try {
            api.send(HttpMethod.DELETE, api.url("/playlists/" + playlistId + "/followers"), null, token);
        } catch (MusicProviderUnavailableException e) {
            if (!(e.getCause() instanceof HttpClientErrorException.NotFound)) {
                throw e;
            }
        }
    }

    static Optional<String> currentUserId(SpotifyApi api, String accessToken) {
        try {
            return Optional.of(api.get(api.url("/me"), accessToken).path("id").asString("")).filter(id -> !id.isEmpty());
        } catch (MusicProviderUnavailableException e) {
            return Optional.empty();
        }
    }
}
