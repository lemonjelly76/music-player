package io.github.lemonjelly76.musicplayer.adapter.lidarr;

import io.github.lemonjelly76.musicplayer.domain.port.AlbumWishlist;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(LidarrProperties.class)
class LidarrConfiguration {

    @Bean
    AlbumWishlist lidarrWishlist(LidarrProperties properties, RestClient.Builder restClientBuilder) {
        return new LidarrWishlist(properties, restClientBuilder);
    }
}
