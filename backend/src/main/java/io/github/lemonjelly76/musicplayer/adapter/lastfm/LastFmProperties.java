package io.github.lemonjelly76.musicplayer.adapter.lastfm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Last.fm's API, free for non-commercial use with a personal API key (ADR-009).
 */
@ConfigurationProperties("lastfm")
public record LastFmProperties(String apiUrl, String apiKey, String userAgent) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
