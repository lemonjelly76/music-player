package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.domain.category.Category;

import java.util.Optional;

/**
 * The app names an activity by its category id; "none", or leaving it out, is Mainstream.
 */
final class ActivityParameter {

    static final String MAINSTREAM = "none";

    private ActivityParameter() {
    }

    static Optional<Category> resolve(CategoryService categories, String category) {
        return category == null || category.isBlank() || category.equals(MAINSTREAM)
                ? Optional.empty()
                : Optional.of(categories.find(category));
    }
}
