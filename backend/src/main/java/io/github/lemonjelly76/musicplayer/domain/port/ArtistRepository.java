package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtist;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtistId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.util.List;

/**
 * The artists each person has rated.
 */
public interface ArtistRepository {

    List<LibraryArtist> findByPerson(PersonId person);

    void save(LibraryArtist artist);

    void delete(LibraryArtistId id);
}
