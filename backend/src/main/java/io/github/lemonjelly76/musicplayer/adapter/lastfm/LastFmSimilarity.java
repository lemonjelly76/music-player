package io.github.lemonjelly76.musicplayer.adapter.lastfm;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.port.SimilaritySource;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Last.fm knows similar artists, not similar albums, so a seed leads to the top albums of its artist's closest
 * neighbours, plus the artist's own other best-known albums.
 */
class LastFmSimilarity implements SimilaritySource {

    static final int SIMILAR_ARTISTS = 5;
    static final int ALBUMS_PER_ARTIST = 2;
    static final int OWN_ALBUMS = 3;

    private final LastFmApi api;

    LastFmSimilarity(LastFmApi api) {
        this.api = api;
    }

    @Override
    public String name() {
        return "lastfm";
    }

    @Override
    public List<AlbumRef> similarTo(AlbumRef seed, int limit) {
        if (!api.configured()) {
            return List.of();
        }
        List<AlbumRef> albums = new ArrayList<>(topAlbums(seed.artist(), OWN_ALBUMS));
        for (String artist : similarArtists(seed.artist())) {
            albums.addAll(topAlbums(artist, ALBUMS_PER_ARTIST));
        }
        return albums.stream().filter(album -> !album.sameAlbumAs(seed)).limit(limit).toList();
    }

    private List<String> similarArtists(String artist) {
        List<String> names = new ArrayList<>();
        api.call("artist.getsimilar", Map.of("artist", artist, "limit", String.valueOf(SIMILAR_ARTISTS),
                        "autocorrect", "1"), true)
                .ifPresent(body -> body.path("similarartists").path("artist")
                        .forEach(similar -> names.add(similar.path("name").asString())));
        return names;
    }

    private List<AlbumRef> topAlbums(String artist, int count) {
        List<AlbumRef> albums = new ArrayList<>();
        api.call("artist.gettopalbums", Map.of("artist", artist, "limit", String.valueOf(count + 2),
                        "autocorrect", "1"), true)
                .ifPresent(body -> {
                    for (JsonNode album : body.path("topalbums").path("album")) {
                        String title = album.path("name").asString("").strip();
                        String albumArtist = album.path("artist").path("name").asString(artist);
                        if (!title.isEmpty() && !title.equals("(null)") && albums.size() < count) {
                            albums.add(AlbumRef.of(albumArtist, title));
                        }
                    }
                });
        return albums;
    }
}
