package io.github.lemonjelly76.musicplayer.adapter.spotify;

import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderTokenStore;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.port.ListeningHistory;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(SpotifyProperties.class)
class SpotifyConfiguration {

    @Bean
    SpotifyApi spotifyApi(SpotifyProperties properties, RestClient.Builder restClientBuilder) {
        return new SpotifyApi(properties, restClientBuilder);
    }

    @Bean
    ProviderAuthorization spotifyAuthorization(SpotifyProperties properties, RestClient.Builder restClientBuilder,
            ProviderTokenStore store, Clock clock, SpotifyApi spotifyApi) {
        return new ProviderAuthorization(Provider.SPOTIFY, properties.oauth(), restClientBuilder, store, clock,
                token -> SpotifyMusicProvider.currentUserId(spotifyApi, token));
    }

    @Bean
    MusicProvider spotifyMusicProvider(SpotifyProperties properties, ProviderAuthorization spotifyAuthorization,
            SpotifyApi spotifyApi) {
        return new SpotifyMusicProvider(properties, spotifyAuthorization, spotifyApi);
    }

    @Bean
    ListeningHistory spotifyListeningHistory(ProviderAuthorization spotifyAuthorization, SpotifyApi spotifyApi) {
        return new SpotifyListeningHistory(spotifyAuthorization, spotifyApi);
    }
}
