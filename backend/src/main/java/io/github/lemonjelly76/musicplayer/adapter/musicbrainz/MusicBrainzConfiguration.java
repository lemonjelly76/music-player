package io.github.lemonjelly76.musicplayer.adapter.musicbrainz;

import io.github.lemonjelly76.musicplayer.domain.port.AlbumCatalogue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MusicBrainzProperties.class)
class MusicBrainzConfiguration {

    @Bean
    AlbumCatalogue musicBrainzCatalogue(MusicBrainzProperties properties, RestClient.Builder restClientBuilder) {
        return new MusicBrainzCatalogue(properties, restClientBuilder);
    }
}
