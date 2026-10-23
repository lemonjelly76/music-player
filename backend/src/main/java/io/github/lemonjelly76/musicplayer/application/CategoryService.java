package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CategoryService {

    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Every activity, in the household's order.
     */
    public List<Category> all() {
        return repository.findAll();
    }

    /**
     * Puts the activities in the given order of names or ids, which must name each of them once.
     */
    public void reorder(List<String> namesOrIds) {
        List<CategoryId> order = namesOrIds.stream().map(this::find).map(Category::id).toList();
        Set<CategoryId> all = repository.findAll().stream().map(Category::id).collect(Collectors.toSet());
        if (order.size() != all.size() || !Set.copyOf(order).equals(all)) {
            throw new IllegalArgumentException("Give every activity once, in the order wanted");
        }
        repository.reorder(order);
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
     * The category asked for, or none for Mainstream, whether named or left out.
     */
    public Optional<Category> findOptional(String nameOrId) {
        return nameOrId == null || nameOrId.isBlank() || Category.meansMainstream(nameOrId)
                ? Optional.empty() : Optional.of(find(nameOrId));
    }

    /**
     * Removes the activity for the whole household, with everything rated, picked or made for it.
     */
    public Category remove(String nameOrId) {
        Category category = find(nameOrId);
        repository.delete(category.id());
        return category;
    }

    /**
     * Gives an activity a new name, keeping its rules; no other activity may have it, nor Mainstream's.
     */
    public Category rename(String nameOrId, String newName) {
        Category category = find(nameOrId);
        String name = newName == null ? "" : newName.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("An activity needs a name");
        }
        refuseMainstream(name);
        repository.findAll().stream()
                .filter(other -> !other.id().equals(category.id()) && other.isCalled(name))
                .findFirst()
                .ifPresent(other -> {
                    throw new IllegalArgumentException("There is already an activity called " + other.name());
                });
        Category renamed = category.renamed(name);
        repository.save(renamed);
        return renamed;
    }

    public Category upsert(String name, String rules) {
        refuseMainstream(name);
        Category category = repository.findAll().stream()
                .filter(existing -> existing.isCalled(name))
                .findFirst()
                .map(existing -> existing.withRules(rules))
                .orElseGet(() -> Category.create(name, rules));
        repository.save(category);
        return category;
    }

    private static void refuseMainstream(String name) {
        if (Category.meansMainstream(name)) {
            throw new IllegalArgumentException(Category.MAINSTREAM + " is always there; choose another name");
        }
    }
}
