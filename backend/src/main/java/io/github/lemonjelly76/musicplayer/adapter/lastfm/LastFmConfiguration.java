package io.github.lemonjelly76.musicplayer.adapter.lastfm;

import io.github.lemonjelly76.musicplayer.domain.port.ListeningHistory;
import io.github.lemonjelly76.musicplayer.domain.port.SimilaritySource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(LastFmProperties.class)
class LastFmConfiguration {

    @Bean
    LastFmApi lastFmApi(LastFmProperties properties, RestClient.Builder restClientBuilder, Clock clock) {
        return new LastFmApi(properties, restClientBuilder, clock);
    }

    @Bean
    SimilaritySource lastFmSimilarity(LastFmApi lastFmApi) {
        return new LastFmSimilarity(lastFmApi);
    }

    @Bean
    ListeningHistory lastFmListeningHistory(LastFmApi lastFmApi) {
        return new LastFmListeningHistory(lastFmApi);
    }
}
