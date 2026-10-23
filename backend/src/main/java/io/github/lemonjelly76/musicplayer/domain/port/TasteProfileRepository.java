package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.taste.TasteProfile;

import java.util.Optional;

public interface TasteProfileRepository {

    /**
     * The person's profile for the activity; no category is Mainstream.
     */
    Optional<TasteProfile> find(PersonId person, Optional<CategoryId> category);

    void save(TasteProfile profile);
}
