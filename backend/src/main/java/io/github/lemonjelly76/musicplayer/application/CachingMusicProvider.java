package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Names;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.LookupCache;
import io.github.lemonjelly76.musicplayer.domain.port.LookupCache.Lookup;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A service that remembers which song or album it found for each name, so playlists, charts and ratings that name
 * the same music again do not use up the service's request limits. A find is asked again after a month, in case the
 * service's copy changed, and one that found nothing after a week, in case it was added since.
 */
public class CachingMusicProvider implements MusicProvider {

    private static final Duration FOUND_FOR = Duration.ofDays(30);
    private static final Duration MISSING_FOR = Duration.ofDays(7);

    private final MusicProvider service;
    private final LookupCache cache;
    private final Clock clock;

    public CachingMusicProvider(MusicProvider service, LookupCache cache, Clock clock) {
        this.service = service;
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    public Optional<Track> findTrack(String artist, String title) {
        String key = key(artist, title);
        return remembered(cache.track(name(), key))
                .orElseGet(() -> lookUp(() -> service.findTrack(artist, title),
                        lookup -> cache.saveTrack(name(), key, lookup)));
    }

    @Override
    public Optional<AlbumRef> findAlbum(String artist, String title) {
        String key = key(artist, title);
        return remembered(cache.album(name(), key))
                .orElseGet(() -> lookUp(() -> service.findAlbum(artist, title),
                        lookup -> cache.saveAlbum(name(), key, lookup)));
    }

    private <T> Optional<Optional<T>> remembered(Optional<Lookup<T>> lookup) {
        return lookup.filter(found -> found.at().plus(found.found().isPresent() ? FOUND_FOR : MISSING_FOR)
                        .isAfter(clock.instant()))
                .map(Lookup::found);
    }

    private <T> Optional<T> lookUp(Supplier<Optional<T>> find, Consumer<Lookup<T>> save) {
        Optional<T> found = find.get();
        save.accept(new Lookup<>(found, clock.instant()));
        return found;
    }

    private static String key(String artist, String title) {
        return Names.normalise(artist) + "|" + Names.normalise(title);
    }

    @Override
    public Provider name() {
        return service.name();
    }

    @Override
    public boolean configured() {
        return service.configured();
    }

    @Override
    public boolean connected(PersonId person) {
        return service.connected(person);
    }

    @Override
    public List<AlbumRef> searchAlbums(String query, int limit) {
        return service.searchAlbums(query, limit);
    }

    @Override
    public Optional<AlbumRef> album(String id) {
        return service.album(id);
    }

    @Override
    public List<Track> searchTracks(String query, int limit) {
        return service.searchTracks(query, limit);
    }

    @Override
    public List<AlbumRef> similarAlbums(String id, int limit) {
        return service.similarAlbums(id, limit);
    }

    @Override
    public List<AlbumRef> favouriteAlbums(PersonId person) {
        return service.favouriteAlbums(person);
    }

    @Override
    public void addFavourite(PersonId person, String id) {
        service.addFavourite(person, id);
    }

    @Override
    public String replacePlaylist(PersonId person, Optional<String> previousPlaylistId, String name,
            String description, List<String> trackIds) {
        return service.replacePlaylist(person, previousPlaylistId, name, description, trackIds);
    }

    @Override
    public URI albumLink(String id) {
        return service.albumLink(id);
    }

    @Override
    public URI searchLink(String query) {
        return service.searchLink(query);
    }

    @Override
    public void renamePlaylist(PersonId person, String playlistId, String newName) {
        service.renamePlaylist(person, playlistId, newName);
    }

    @Override
    public void deletePlaylist(PersonId person, String playlistId) {
        service.deletePlaylist(person, playlistId);
    }

    @Override
    public URI trackLink(String id) {
        return service.trackLink(id);
    }

    @Override
    public URI playlistLink(String playlistId) {
        return service.playlistLink(playlistId);
    }
}
