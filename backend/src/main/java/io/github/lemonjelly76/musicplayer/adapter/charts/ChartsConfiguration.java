package io.github.lemonjelly76.musicplayer.adapter.charts;

import io.github.lemonjelly76.musicplayer.domain.port.ChartSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ChartsProperties.class)
class ChartsConfiguration {

    @Bean
    ChartSource appleMusicCharts(ChartsProperties properties, RestClient.Builder restClientBuilder, Clock clock) {
        return new AppleMusicCharts(properties, restClientBuilder, clock);
    }
}
