package io.github.lemonjelly76.musicplayer.adapter.tidal;

import io.github.lemonjelly76.musicplayer.adapter.oauth.OAuthProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tidal's API (ADR-006): the catalogue is read in one country, and people sign in at Tidal's login page.
 */
@ConfigurationProperties("tidal")
public record TidalProperties(String apiUrl, String countryCode, String webUrl, OAuthProperties oauth) {
}
