package io.github.lemonjelly76.musicplayer.domain.playlist;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AudioFormat;
import io.github.lemonjelly76.musicplayer.domain.album.AudioFormats;
import io.github.lemonjelly76.musicplayer.domain.album.Names;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A song on one streaming service, with the album it is from; several artists are named together, comma separated.
 * {@code formats} are the qualities beyond the ordinary it can be played in, when the service said.
 */
public record Track(Provider provider, String id, String artist, String title, String album, Optional<String> cover,
                    Set<AudioFormat> formats) {

    private static final Pattern DASH_EDITION = Pattern.compile(
            "\\s+[-–]\\s+[^-–]*\\b(remaster|remastered|radio edit|single version|album version|mono|stereo)\\b.*$",
            Pattern.CASE_INSENSITIVE);

    public Track {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(cover, "cover");
        formats = AudioFormats.copyOf(formats);
    }

    public Track(Provider provider, String id, String artist, String title, String album, Optional<String> cover) {
        this(provider, id, artist, title, album, cover, Set.of());
    }

    public Track withCover(Optional<String> newCover) {
        return new Track(provider, id, artist, title, album, newCover, formats);
    }

    /**
     * The same song whatever its edition or guests, so a playlist or library holds it once: "Air, Beth Hirsch — All I
     * Need (feat. Beth Hirsch)" and "Air — All I Need - Remastered" are one song. Live takes and remixes stay apart.
     */
    public String songKey() {
        String plainTitle = DASH_EDITION.matcher(Names.withoutGuests(title)).replaceAll("");
        return Names.normalise(artist.split(",")[0]) + "|" + Names.title(plainTitle);
    }

    public AlbumKey albumKey() {
        return AlbumKey.of(artist, album);
    }

    public String label() {
        return artist + " — " + title;
    }
}
