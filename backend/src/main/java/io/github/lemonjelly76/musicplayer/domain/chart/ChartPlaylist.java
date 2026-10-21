package io.github.lemonjelly76.musicplayer.domain.chart;

import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Whether a person wants the week's chart as a playlist in their account, and the one last written there.
 */
public record ChartPlaylist(PersonId person, boolean wanted, Optional<SyncedPlaylist> synced, Optional<Instant> writtenAt) {

    public ChartPlaylist {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(synced, "synced");
        Objects.requireNonNull(writtenAt, "writtenAt");
    }

    /**
     * Everyone gets the playlist until they turn it off.
     */
    public static ChartPlaylist wantedBy(PersonId person) {
        return new ChartPlaylist(person, true, Optional.empty(), Optional.empty());
    }

    public ChartPlaylist wanted(boolean wanted) {
        return new ChartPlaylist(person, wanted, synced, writtenAt);
    }

    public ChartPlaylist writtenTo(SyncedPlaylist playlist, Instant at) {
        return new ChartPlaylist(person, wanted, Optional.of(playlist), Optional.of(at));
    }
}
