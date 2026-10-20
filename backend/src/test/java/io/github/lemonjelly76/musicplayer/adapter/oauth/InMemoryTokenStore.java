package io.github.lemonjelly76.musicplayer.adapter.oauth;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps tokens in memory so the OAuth and provider adapters are tested without a database.
 */
public class InMemoryTokenStore extends ProviderTokenStore {

    private final Map<String, ProviderTokens> tokens = new HashMap<>();

    public InMemoryTokenStore() {
        super(null);
    }

    @Override
    public Optional<ProviderTokens> find(PersonId person, Provider provider) {
        return Optional.ofNullable(tokens.get(person + provider.name()));
    }

    @Override
    public void save(PersonId person, Provider provider, ProviderTokens saved) {
        tokens.put(person + provider.name(), saved);
    }

    @Override
    public void clear(PersonId person, Provider provider) {
        tokens.remove(person + provider.name());
    }
}
