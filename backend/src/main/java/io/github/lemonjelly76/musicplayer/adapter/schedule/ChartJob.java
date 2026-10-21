package io.github.lemonjelly76.musicplayer.adapter.schedule;

import io.github.lemonjelly76.musicplayer.application.ChartService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Keeps the week's chart up to date and writes each person's chart playlist once a week, with no model involved.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "charts.refresh", havingValue = "true", matchIfMissing = true)
class ChartJob {

    private static final System.Logger LOG = System.getLogger(ChartJob.class.getName());

    private final ChartService charts;

    ChartJob(ChartService charts) {
        this.charts = charts;
    }

    @Scheduled(fixedDelayString = "${charts.check-interval}", initialDelayString = "${charts.initial-delay}")
    void refresh() {
        try {
            charts.refreshIfStale();
            int written = charts.writeDuePlaylists();
            if (written > 0) {
                LOG.log(System.Logger.Level.INFO, "Wrote {0} chart playlists", written);
            }
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not refresh the chart", e);
        }
    }
}
