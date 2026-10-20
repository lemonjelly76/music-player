package io.github.lemonjelly76.musicplayer.adapter.spotify;

import io.github.lemonjelly76.musicplayer.adapter.oauth.OAuthProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spotify's Web API (ADR-006), in Development Mode: up to five people, and search pages of at most ten.
 */
@ConfigurationProperties("spotify")
public record SpotifyProperties(String apiUrl, String market, String webUrl, OAuthProperties oauth) {
}
