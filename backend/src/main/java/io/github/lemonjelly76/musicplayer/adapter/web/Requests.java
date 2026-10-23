package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class Requests {

    private Requests() {
    }

    /**
     * A rating of 1 (dislike) to 3 (love) for an activity; no category, or "none", is Mainstream, and no rating
     * removes the activity's own rating.
     */
    record RatingRequest(String albumRef, String categoryId, Integer rating, String comment) {
    }

    /**
     * A song rated from the app, which sends it as it showed it, with a rating of 1 (dislike) to 3 (love) for an
     * activity; no category, or "none", is Mainstream, and no rating removes the activity's own rating.
     */
    record SongRatingRequest(String ref, String artist, String title, String album, String cover, String categoryId,
                             Integer rating) {

        Track track() {
            String[] parts = Objects.requireNonNullElse(ref, "").split(":", 2);
            if (parts.length != 2 || parts[1].isBlank() || artist == null || artist.isBlank() || title == null
                    || title.isBlank()) {
                throw new IllegalArgumentException("A song needs a ref such as spotify:<id>, an artist and a title");
            }
            return new Track(Provider.parse(parts[0]), parts[1], artist.strip(), title.strip(),
                    Objects.requireNonNullElse(album, "").strip(), Optional.ofNullable(cover).filter(url -> !url.isBlank()));
        }
    }

    /**
     * Every activity's id, in the order wanted.
     */
    record CategoryOrderRequest(List<String> ids) {
    }

    /**
     * A new name for an activity.
     */
    record CategoryRequest(String name) {
    }

    /**
     * A null or empty service leaves the person without one.
     */
    record ServiceRequest(String service) {
    }

    record ChartPlaylistRequest(Boolean enabled) {
    }

    record PlaylistSlotRequest(Integer slot) {
    }

    /**
     * A category with {@code enabled} null follows the person's own setting again.
     */
    record TrackingRequest(String categoryId, Boolean enabled) {
    }
}
