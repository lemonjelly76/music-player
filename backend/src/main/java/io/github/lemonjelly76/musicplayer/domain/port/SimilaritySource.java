package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;

import java.util.List;

/**
 * Suggests albums that people who like the seed also like. Sources that cannot answer return nothing rather than
 * failing, so one source being down only narrows the pool.
 */
public interface SimilaritySource {

    String name();

    List<AlbumRef> similarTo(AlbumRef seed, int limit);
}
