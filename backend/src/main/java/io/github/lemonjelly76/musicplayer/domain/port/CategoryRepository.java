package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    List<Category> findAll();

    Optional<Category> findById(CategoryId id);

    void save(Category category);

    /**
     * Removes the category for everyone, with every rating, album list and playlist for it; albums rated for nothing
     * else leave their library. Plays from it count as general listening.
     */
    void delete(CategoryId id);
}
