package io.github.lemonjelly76.musicplayer.domain.album;

import java.util.Objects;
import java.util.Optional;

/**
 * The same album's ids in each catalogue that knows it.
 */
public record ProviderIds(Optional<String> tidal, Optional<String> spotify, Optional<String> musicBrainz) {

    public static final ProviderIds NONE = new ProviderIds(Optional.empty(), Optional.empty(), Optional.empty());

    public ProviderIds {
        tidal = clean(tidal);
        spotify = clean(spotify);
        musicBrainz = clean(musicBrainz);
    }

    public static ProviderIds of(Provider provider, String id) {
        return NONE.with(provider, id);
    }

    public static ProviderIds musicBrainz(String mbid) {
        return new ProviderIds(Optional.empty(), Optional.empty(), Optional.of(mbid));
    }

    public Optional<String> get(Provider provider) {
        return switch (provider) {
            case TIDAL -> tidal;
            case SPOTIFY -> spotify;
        };
    }

    public ProviderIds with(Provider provider, String id) {
        return switch (provider) {
            case TIDAL -> new ProviderIds(Optional.of(id), spotify, musicBrainz);
            case SPOTIFY -> new ProviderIds(tidal, Optional.of(id), musicBrainz);
        };
    }

    /**
     * These ids, with the other's filling any gaps.
     */
    public ProviderIds merge(ProviderIds other) {
        return new ProviderIds(tidal.or(other::tidal), spotify.or(other::spotify), musicBrainz.or(other::musicBrainz));
    }

    private static Optional<String> clean(Optional<String> id) {
        return Objects.requireNonNull(id, "id").map(String::strip).filter(text -> !text.isEmpty());
    }
}
