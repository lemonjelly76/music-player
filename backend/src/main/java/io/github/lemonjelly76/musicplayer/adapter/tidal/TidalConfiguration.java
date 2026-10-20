package io.github.lemonjelly76.musicplayer.adapter.tidal;

import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderTokenStore;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(TidalProperties.class)
class TidalConfiguration {

    @Bean
    ProviderAuthorization tidalAuthorization(TidalProperties properties, RestClient.Builder restClientBuilder,
            ProviderTokenStore store, Clock clock) {
        return new ProviderAuthorization(Provider.TIDAL, properties.oauth(), restClientBuilder, store, clock,
                token -> TidalMusicProvider.currentUserId(properties, restClientBuilder, token));
    }

    @Bean
    MusicProvider tidalMusicProvider(TidalProperties properties, ProviderAuthorization tidalAuthorization,
            RestClient.Builder restClientBuilder) {
        return new TidalMusicProvider(properties, tidalAuthorization, restClientBuilder);
    }
}
