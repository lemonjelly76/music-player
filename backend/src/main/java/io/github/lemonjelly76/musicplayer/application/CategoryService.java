package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CategoryService {

    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    public List<Category> all() {
        return repository.findAll().stream().sorted(Comparator.comparing(Category::name)).toList();
    }

    public Map<CategoryId, String> names() {
        return repository.findAll().stream().collect(Collectors.toMap(Category::id, Category::name));
    }

    public Category find(String nameOrId) {
        return Ids.uuid(nameOrId).flatMap(id -> repository.findById(new CategoryId(id)))
                .or(() -> repository.findAll().stream().filter(category -> category.isCalled(nameOrId)).findFirst())
                .orElseThrow(() -> new NotFoundException("No category called '" + nameOrId.strip()
                        + "'; use list_categories, or upsert_category to add it"));
    }

    /**
     * The category asked for, or none when the text is empty: a list for everything.
     */
    public Optional<Category> findOptional(String nameOrId) {
        return nameOrId == null || nameOrId.isBlank() ? Optional.empty() : Optional.of(find(nameOrId));
    }

    /**
     * Removes the activity for the whole household, with everything rated, picked or made for it.
     */
    public Category remove(String nameOrId) {
        Category category = find(nameOrId);
        repository.delete(category.id());
        return category;
    }

    public Category upsert(String name, String rules) {
        Category category = repository.findAll().stream()
                .filter(existing -> existing.isCalled(name))
                .findFirst()
                .map(existing -> existing.withRules(rules))
                .orElseGet(() -> Category.create(name, rules));
        repository.save(category);
        return category;
    }
}
