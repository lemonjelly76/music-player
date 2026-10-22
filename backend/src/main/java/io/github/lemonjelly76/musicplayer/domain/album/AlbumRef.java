package io.github.lemonjelly76.musicplayer.domain.album;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An album as people name it, with whatever catalogue ids are known for it, a link to its cover art when a
 * catalogue gave one, and the formats beyond the ordinary it can be played in, when a service said.
 */
public record AlbumRef(String artist, String title, Optional<Integer> year, ProviderIds ids, Optional<String> cover,
                       Set<AudioFormat> formats) {

    public AlbumRef {
        artist = Objects.requireNonNull(artist, "artist").strip();
        title = Objects.requireNonNull(title, "title").strip();
        if (artist.isEmpty() || title.isEmpty()) {
            throw new IllegalArgumentException("an album needs an artist and a title");
        }
        Objects.requireNonNull(year, "year");
        Objects.requireNonNull(ids, "ids");
        cover = Objects.requireNonNull(cover, "cover").map(String::strip).filter(url -> !url.isEmpty());
        formats = AudioFormats.copyOf(formats);
    }

    public AlbumRef(String artist, String title, Optional<Integer> year, ProviderIds ids, Optional<String> cover) {
        this(artist, title, year, ids, cover, Set.of());
    }

    public AlbumRef(String artist, String title, Optional<Integer> year, ProviderIds ids) {
        this(artist, title, year, ids, Optional.empty());
    }

    public static AlbumRef of(String artist, String title) {
        return new AlbumRef(artist, title, Optional.empty(), ProviderIds.NONE);
    }

    public AlbumKey key() {
        return AlbumKey.of(artist, title);
    }

    public boolean sameAlbumAs(AlbumRef other) {
        return key().equals(other.key());
    }

    /**
     * This album with the other's ids, year and cover filling any gaps; names stay as they are.
     */
    public AlbumRef enrichedWith(AlbumRef other) {
        return new AlbumRef(artist, title, year.or(other::year), ids.merge(other.ids), cover.or(other::cover),
                formats.isEmpty() ? other.formats : formats);
    }

    public AlbumRef withIds(ProviderIds newIds) {
        return new AlbumRef(artist, title, year, newIds, cover, formats);
    }

    public AlbumRef withCover(String url) {
        return new AlbumRef(artist, title, year, ids, Optional.of(url), formats);
    }

    public Optional<Integer> decade() {
        return year.map(value -> value / 10 * 10);
    }

    public String label() {
        return artist + " — " + title;
    }
}
