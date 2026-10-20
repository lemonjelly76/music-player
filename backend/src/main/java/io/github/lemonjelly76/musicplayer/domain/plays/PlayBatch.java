package io.github.lemonjelly76.musicplayer.domain.plays;

import java.util.List;
import java.util.Optional;

/**
 * Plays read from a listening history since the last cursor, and the cursor to read from next time.
 */
public record PlayBatch(List<Play> plays, Optional<String> nextCursor) {

    public PlayBatch {
        plays = List.copyOf(plays);
    }

    public static PlayBatch empty(Optional<String> cursor) {
        return new PlayBatch(List.of(), cursor);
    }
}
