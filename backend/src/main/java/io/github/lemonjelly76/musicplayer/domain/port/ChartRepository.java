package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartPlaylist;
import io.github.lemonjelly76.musicplayer.domain.chart.ResolvedChart;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.util.Optional;

/**
 * The latest chart as found on each service, and each person's chart playlist.
 */
public interface ChartRepository {

    /**
     * Replaces the service's chart with this one.
     */
    void save(ResolvedChart chart);

    Optional<ResolvedChart> find(Provider provider);

    /**
     * The person's chart playlist; someone never asked has it wanted and not yet written.
     */
    ChartPlaylist playlistFor(PersonId person);

    void save(ChartPlaylist playlist);
}
