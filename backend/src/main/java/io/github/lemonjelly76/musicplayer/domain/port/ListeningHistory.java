package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayBatch;

import java.util.Optional;

/**
 * Where a person's recent plays can be read: their streaming service's history or their Last.fm scrobbles.
 */
public interface ListeningHistory {

    String source();

    boolean covers(Person person);

    PlayBatch playsSince(Person person, Optional<String> cursor);
}
