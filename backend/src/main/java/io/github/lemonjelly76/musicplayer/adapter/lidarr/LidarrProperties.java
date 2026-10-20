package io.github.lemonjelly76.musicplayer.adapter.lidarr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The household's Lidarr (ADR-012), and where a new artist's files go when an album is wanted from them.
 */
@ConfigurationProperties("lidarr")
public record LidarrProperties(String url, String apiKey, String rootFolder, int qualityProfileId,
                               int metadataProfileId) {

    public boolean configured() {
        return url != null && !url.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
