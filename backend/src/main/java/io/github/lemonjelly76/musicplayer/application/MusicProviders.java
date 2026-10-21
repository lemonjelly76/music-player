package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * The streaming services the app is registered with, and which one serves each person.
 */
public class MusicProviders {

    private final List<MusicProvider> providers;

    public MusicProviders(List<MusicProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public Optional<MusicProvider> find(Provider name) {
        return providers.stream().filter(provider -> provider.name() == name && provider.configured()).findFirst();
    }

    public MusicProvider get(Provider name) {
        return find(name).orElseThrow(() -> new MusicProviderUnavailableException(
                "The app is not registered with " + name.prefix() + "; set its client id and secret"));
    }

    /**
     * The person's own service when the app is registered with it.
     */
    public Optional<MusicProvider> forPerson(Person person) {
        return person.provider().flatMap(this::find);
    }

    /**
     * The person's own service, else the first registered one (Tidal before Spotify), for catalogue searches.
     */
    public Optional<MusicProvider> catalogueFor(Optional<Person> person) {
        return person.flatMap(this::forPerson)
                .or(() -> List.of(Provider.values()).stream().flatMap(name -> find(name).stream()).findFirst());
    }

    /**
     * Opens the album in the person's service; when its id there is not known yet, opens a search for it instead.
     */
    public Optional<URI> link(Person person, AlbumRef album) {
        return forPerson(person).map(provider -> album.ids().get(provider.name())
                .map(provider::albumLink)
                .orElseGet(() -> provider.searchLink(album.artist() + " " + album.title())));
    }

    public Optional<URI> link(SyncedPlaylist playlist) {
        return find(playlist.provider()).map(provider -> provider.playlistLink(playlist.playlistId()));
    }
}
