package io.github.lemonjelly76.musicplayer.domain.chart;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A country's most played songs and albums, as a chart publishes them.
 */
public record Chart(String country, Instant fetchedAt, List<ChartEntry> songs, List<ChartEntry> albums) {

    public Chart {
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        songs = List.copyOf(songs);
        albums = List.copyOf(albums);
    }
}
