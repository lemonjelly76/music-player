package io.github.lemonjelly76.musicplayer.domain.person;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;

import java.util.Objects;
import java.util.Optional;

/**
 * Someone in the household with their own library, taste profile and lists. Their streaming service is where their
 * playlists and favourites land; their Last.fm user, when set, is where their plays are read from.
 */
public record Person(PersonId id, String name, Optional<Provider> provider, boolean playTracking,
                     Optional<String> lastFmUser) {

    public Person {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("a person needs a name");
        }
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(lastFmUser, "lastFmUser");
        lastFmUser = lastFmUser.map(String::strip).filter(user -> !user.isEmpty());
    }

    public static Person create(String name, Optional<Provider> provider) {
        return new Person(PersonId.random(), name, provider, true, Optional.empty());
    }

    public Person withProvider(Optional<Provider> newProvider) {
        return new Person(id, name, newProvider, playTracking, lastFmUser);
    }

    public Person withPlayTracking(boolean enabled) {
        return new Person(id, name, provider, enabled, lastFmUser);
    }

    public Person withLastFmUser(Optional<String> user) {
        return new Person(id, name, provider, playTracking, user);
    }

    public boolean isCalled(String text) {
        return name.equalsIgnoreCase(text.strip());
    }
}
