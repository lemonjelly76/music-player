package io.github.lemonjelly76.musicplayer.adapter.web;

import java.util.List;

final class Requests {

    private Requests() {
    }

    record FeedbackRequest(String verdict, String comment) {
    }

    record LibraryRequest(String albumRef, Integer rating, String notes, List<String> tags) {
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
