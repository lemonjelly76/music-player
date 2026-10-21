package io.github.lemonjelly76.musicplayer.adapter.web;

final class Requests {

    private Requests() {
    }

    /**
     * A rating of 1 (dislike) to 3 (love) for an activity; no category, or "none", is General, and no rating
     * removes the activity's own rating.
     */
    record RatingRequest(String albumRef, String categoryId, Integer rating, String comment) {
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

    /**
     * A category with {@code enabled} null follows the person's own setting again.
     */
    record TrackingRequest(String categoryId, Boolean enabled) {
    }
}
