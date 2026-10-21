package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySongId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.util.List;

/**
 * The songs each person has rated.
 */
public interface SongRepository {

    List<LibrarySong> findByPerson(PersonId person);

    void save(LibrarySong song);

    void delete(LibrarySongId id);
}
