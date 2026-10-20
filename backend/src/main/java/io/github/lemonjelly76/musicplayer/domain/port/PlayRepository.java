package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plays kept one per track, rolled up to albums when read, plus the switches that say whether to keep them.
 */
public interface PlayRepository {

    /**
     * Saves the plays, ignoring any already saved for the same moment and source.
     */
    void add(PersonId person, Collection<CategorisedPlay> plays);

    /**
     * Album totals since the given moment, most played first; a category narrows them to plays from its lists.
     */
    List<AlbumPlays> albumTotals(PersonId person, Optional<CategoryId> category, Instant since);

    void clear(PersonId person, Optional<CategoryId> category);

    Map<CategoryId, Boolean> trackingOverrides(PersonId person);

    void setTrackingOverride(PersonId person, CategoryId category, Optional<Boolean> enabled);

    Optional<String> cursor(PersonId person, String source);

    void saveCursor(PersonId person, String source, String cursor);

    record CategorisedPlay(Play play, Optional<CategoryId> category) {
    }
}
