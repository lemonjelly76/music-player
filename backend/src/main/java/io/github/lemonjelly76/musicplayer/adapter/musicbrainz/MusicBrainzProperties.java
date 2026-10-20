package io.github.lemonjelly76.musicplayer.adapter.musicbrainz;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MusicBrainz asks every app to name itself and a contact in the User-Agent, and to make at most one request a
 * second.
 */
@ConfigurationProperties("musicbrainz")
public record MusicBrainzProperties(String apiUrl, String userAgent) {
}
