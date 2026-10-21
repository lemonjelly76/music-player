package io.github.lemonjelly76.musicplayer.domain.playlist;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.Names;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A song on one streaming service, with the album it is from; several artists are named together, comma separated.
 */
public record Track(Provider provider, String id, String artist, String title, String album, Optional<String> cover) {

    private static final Pattern GUESTS = Pattern.compile("[(\\[](feat\\.?|ft\\.?|featuring|with) [^)\\]]*[)\\]]",
            Pattern.CASE_INSENSITIVE);
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
    }

    /**
     * The same song whatever its edition or guests, so a playlist or library holds it once: "Air, Beth Hirsch — All I
     * Need (feat. Beth Hirsch)" and "Air — All I Need - Remastered" are one song. Live takes and remixes stay apart.
     */
    public String songKey() {
        String plainTitle = DASH_EDITION.matcher(GUESTS.matcher(title).replaceAll("")).replaceAll("");
        return Names.normalise(artist.split(",")[0]) + "|" + Names.title(plainTitle);
    }

    public AlbumKey albumKey() {
        return AlbumKey.of(artist, album);
    }

    public String label() {
        return artist + " — " + title;
    }
}
