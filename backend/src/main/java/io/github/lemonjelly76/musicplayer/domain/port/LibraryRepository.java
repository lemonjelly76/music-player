package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbumId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;

import java.util.List;
import java.util.Optional;

public interface LibraryRepository {

    List<LibraryAlbum> findByPerson(PersonId person);

    Optional<LibraryAlbum> findById(LibraryAlbumId id);

    void save(LibraryAlbum album);

    void delete(LibraryAlbumId id);
}
