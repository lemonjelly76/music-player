package io.github.lemonjelly76.musicplayer.domain.plays;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A summary of what a person played over a period: how much, and their most-played albums and artists.
 */
public record ListeningStats(int tracks, int albums, List<AlbumPlays> topAlbums, List<ArtistPlays> topArtists) {

    public ListeningStats {
        topAlbums = List.copyOf(topAlbums);
        topArtists = List.copyOf(topArtists);
    }

    /**
     * Summarises album totals, which come most played first, keeping the top few albums and artists.
     */
    public static ListeningStats of(List<AlbumPlays> totals, int top) {
        int tracks = totals.stream().mapToInt(AlbumPlays::plays).sum();
        return new ListeningStats(tracks, totals.size(), totals.stream().limit(top).toList(),
                artists(totals).stream().limit(top).toList());
    }

    /**
     * Artists by plays, then by how many of their albums were played; each is named as on their most-played album.
     */
    private static List<ArtistPlays> artists(List<AlbumPlays> totals) {
        Map<String, ArtistPlays> byArtist = new LinkedHashMap<>();
        for (AlbumPlays total : totals) {
            String artist = total.album().artist();
            byArtist.merge(artist.strip().toLowerCase(Locale.ROOT), new ArtistPlays(artist, total.plays(), 1),
                    (sum, more) -> new ArtistPlays(sum.artist(), sum.plays() + more.plays(), sum.albums() + 1));
        }
        return byArtist.values().stream()
                .sorted(Comparator.comparingInt(ArtistPlays::plays).thenComparingInt(ArtistPlays::albums).reversed())
                .toList();
    }
}
