package io.github.lemonjelly76.musicplayer.domain.album;

import java.util.List;
import java.util.Optional;

/**
 * Picks the catalogue's album for an artist and title from its search results: an exact match on the album key,
 * else the same artist with a title that starts with the one asked for ("OK Computer OKNOTOK 1997 2017").
 */
public final class AlbumMatch {

    private AlbumMatch() {
    }

    public static Optional<AlbumRef> best(List<AlbumRef> found, String artist, String title) {
        AlbumKey wanted = AlbumKey.of(artist, title);
        return found.stream().filter(album -> album.key().equals(wanted)).findFirst()
                .or(() -> found.stream().filter(album -> startsWithTitle(album.key(), wanted)).findFirst());
    }

    private static boolean startsWithTitle(AlbumKey candidate, AlbumKey wanted) {
        String[] candidateParts = candidate.value().split("\\|", 2);
        String[] wantedParts = wanted.value().split("\\|", 2);
        return candidateParts[0].equals(wantedParts[0]) && !wantedParts[1].isEmpty()
                && candidateParts[1].startsWith(wantedParts[1] + " ");
    }
}
