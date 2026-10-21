package io.github.lemonjelly76.musicplayer.adapter.charts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Apple Music's public chart feeds, which need no account: {@code country} is a two-letter store code such as gb.
 */
@ConfigurationProperties("charts")
public record ChartsProperties(String url, String country, int size) {
}
