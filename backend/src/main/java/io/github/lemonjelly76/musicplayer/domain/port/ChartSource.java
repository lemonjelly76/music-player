package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.chart.Chart;

/**
 * Where the most played songs and albums come from.
 */
public interface ChartSource {

    Chart latest();
}
