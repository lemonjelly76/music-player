package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.domain.category.Category;

import java.util.Optional;

/**
 * The app names an activity by its category id; "none", or leaving it out, is General.
 */
final class ActivityParameter {

    static final String GENERAL = "none";

    private ActivityParameter() {
    }

    static Optional<Category> resolve(CategoryService categories, String category) {
        return category == null || category.isBlank() || category.equals(GENERAL)
                ? Optional.empty()
                : Optional.of(categories.find(category));
    }
}
