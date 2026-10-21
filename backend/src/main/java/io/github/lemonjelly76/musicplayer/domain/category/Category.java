package io.github.lemonjelly76.musicplayer.domain.category;

import java.util.Objects;

/**
 * An activity or mood shared by everyone, such as Running or Focus, with the rules a list for it must follow.
 */
public record Category(CategoryId id, String name, String rules) {

    public Category {
        Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("a category needs a name");
        }
        rules = Objects.requireNonNull(rules, "rules").strip();
    }

    public static Category create(String name, String rules) {
        return new Category(CategoryId.random(), name, rules);
    }

    public Category withRules(String newRules) {
        return new Category(id, name, newRules);
    }

    public Category renamed(String newName) {
        return new Category(id, newName, rules);
    }

    public boolean isCalled(String text) {
        return name.equalsIgnoreCase(text.strip());
    }
}
