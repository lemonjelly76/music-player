package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.taste.TasteProfile;

import java.util.Optional;

public interface TasteProfileRepository {

    Optional<TasteProfile> find(PersonId person);

    void save(TasteProfile profile);
}
