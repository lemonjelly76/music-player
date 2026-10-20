package io.github.lemonjelly76.musicplayer.adapter.schedule;

import io.github.lemonjelly76.musicplayer.application.PlayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Reads everyone's new plays every hour (ADR-010).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "plays.collect", havingValue = "true", matchIfMissing = true)
class PlayCollectionJob {

    private static final System.Logger LOG = System.getLogger(PlayCollectionJob.class.getName());

    private final PlayService plays;

    PlayCollectionJob(PlayService plays) {
        this.plays = plays;
    }

    @Scheduled(fixedDelayString = "${plays.interval}", initialDelayString = "${plays.initial-delay}")
    void collect() {
        int saved = plays.collect();
        if (saved > 0) {
            LOG.log(System.Logger.Level.INFO, "Counted {0} new plays", saved);
        }
    }
}
