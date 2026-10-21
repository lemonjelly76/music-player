package io.github.lemonjelly76.musicplayer.domain.chart;

import java.util.Objects;

/**
 * A place in a chart, as the chart names it.
 */
public record ChartEntry(int position, String artist, String title) {

    public ChartEntry {
        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(title, "title");
    }
}
