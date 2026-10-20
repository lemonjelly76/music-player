package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.port.AlbumCatalogue;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the album references a model or the app sends into real albums. A reference is a catalogue id
 * ({@code tidal:251380836}, {@code spotify:4LH4d3cOWNNsVw41Gqt2kv}, {@code mbid:<release group id>}) or a name,
 * {@code Artist — Title}. Nothing is trusted until a catalogue has confirmed it exists.
 */
public class AlbumResolver {

    private static final Pattern PREFIXED = Pattern.compile("^(tidal|spotify|mbid):(\\S+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMED = Pattern.compile("^(.+?)\\s+[—–-]\\s+(.+)$");

    private final MusicProviders providers;
    private final AlbumCatalogue catalogue;

    public AlbumResolver(MusicProviders providers, AlbumCatalogue catalogue) {
        this.providers = providers;
        this.catalogue = catalogue;
    }

    /**
     * The album, confirmed to exist and, when a service is given, carried by that service with its id.
     */
    public Resolution resolve(String reference, Optional<MusicProvider> playableOn) {
        String text = reference == null ? "" : reference.strip();
        Matcher prefixed = PREFIXED.matcher(text);
        if (prefixed.matches()) {
            String kind = prefixed.group(1).toLowerCase();
            String id = prefixed.group(2);
            Optional<AlbumRef> found = kind.equals("mbid")
                    ? catalogue.byMusicBrainzId(id)
                    : providers.get(Provider.parse(kind)).album(id);
            return found.map(album -> playable(album, playableOn))
                    .orElseGet(() -> Resolution.failed("No album with id " + text));
        }
        Matcher named = NAMED.matcher(text);
        if (!named.matches()) {
            return Resolution.failed("'" + text + "' is not an album reference; use an id from search_catalogue "
                    + "such as tidal:251380836, or 'Artist — Title'");
        }
        String artist = named.group(1);
        String title = named.group(2);
        Optional<AlbumRef> found = playableOn.isPresent()
                ? playableOn.get().findAlbum(artist, title)
                : catalogue.findAlbum(artist, title);
        return found.map(album -> playable(album, playableOn))
                .orElseGet(() -> Resolution.failed(playableOn
                        .map(provider -> artist + " — " + title + " is not on " + provider.name().prefix())
                        .orElse("No album " + artist + " — " + title + " could be found")));
    }

    /**
     * The album a {@code Artist — Title} reference names, before any catalogue is asked; empty for an id.
     */
    public Optional<AlbumRef> named(String reference) {
        Matcher named = NAMED.matcher(reference == null ? "" : reference.strip());
        return named.matches() && !PREFIXED.matcher(reference.strip()).matches()
                ? Optional.of(AlbumRef.of(named.group(1), named.group(2)))
                : Optional.empty();
    }

    /**
     * Like {@link #resolve}, but an album no catalogue knows is kept by name, for a library entry the person
     * vouches for.
     */
    public Optional<AlbumRef> resolveLeniently(String reference, Optional<MusicProvider> playableOn) {
        Resolution resolution = resolve(reference, playableOn);
        if (resolution.album().isPresent()) {
            return resolution.album();
        }
        return named(reference).map(byName -> catalogue.findAlbum(byName.artist(), byName.title())
                .map(byName::enrichedWith).orElse(byName));
    }

    private Resolution playable(AlbumRef album, Optional<MusicProvider> playableOn) {
        if (playableOn.isEmpty() || album.ids().get(playableOn.get().name()).isPresent()) {
            return Resolution.found(album);
        }
        MusicProvider provider = playableOn.get();
        return provider.findAlbum(album.artist(), album.title())
                .map(onService -> Resolution.found(album.enrichedWith(onService)))
                .orElseGet(() -> Resolution.failed(album.label() + " is not on " + provider.name().prefix()));
    }

    public record Resolution(Optional<AlbumRef> album, String problem) {

        static Resolution found(AlbumRef album) {
            return new Resolution(Optional.of(album), "");
        }

        static Resolution failed(String problem) {
            return new Resolution(Optional.empty(), problem);
        }
    }
}
