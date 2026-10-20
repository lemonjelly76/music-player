package io.github.lemonjelly76.musicplayer.domain.album;

import java.util.Objects;
import java.util.Optional;

/**
 * An album as people name it, with whatever catalogue ids are known for it.
 */
public record AlbumRef(String artist, String title, Optional<Integer> year, ProviderIds ids) {

    public AlbumRef {
        artist = Objects.requireNonNull(artist, "artist").strip();
        title = Objects.requireNonNull(title, "title").strip();
        if (artist.isEmpty() || title.isEmpty()) {
            throw new IllegalArgumentException("an album needs an artist and a title");
        }
        Objects.requireNonNull(year, "year");
        Objects.requireNonNull(ids, "ids");
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
     * This album with the other's ids and year filling any gaps; names stay as they are.
     */
    public AlbumRef enrichedWith(AlbumRef other) {
        return new AlbumRef(artist, title, year.or(other::year), ids.merge(other.ids));
    }

    public AlbumRef withIds(ProviderIds newIds) {
        return new AlbumRef(artist, title, year, newIds);
    }

    public Optional<Integer> decade() {
        return year.map(value -> value / 10 * 10);
    }

    public String label() {
        return artist + " — " + title;
    }
}
