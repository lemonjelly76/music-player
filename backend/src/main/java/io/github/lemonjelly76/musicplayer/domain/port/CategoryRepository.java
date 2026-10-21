package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    /**
     * Every category, in the household's order.
     */
    List<Category> findAll();

    Optional<Category> findById(CategoryId id);

    /**
     * Saves the category; a new one goes last.
     */
    void save(Category category);

    /**
     * Puts the categories in the given order, which names each of them once.
     */
    void reorder(List<CategoryId> order);

    /**
     * Removes the category for everyone, with every rating, album list and playlist for it; albums and songs rated for
     * nothing else leave their library. Plays from it count as general listening.
     */
    void delete(CategoryId id);
}
