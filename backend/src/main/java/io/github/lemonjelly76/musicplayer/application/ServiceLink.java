package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;

import java.net.URI;

/**
 * Where an album or song opens on one streaming service.
 */
public record ServiceLink(Provider service, URI link) {
}
