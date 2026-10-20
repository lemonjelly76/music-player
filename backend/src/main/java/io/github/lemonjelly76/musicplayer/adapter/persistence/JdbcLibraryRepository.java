package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbumId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.LibraryRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

@Repository
class JdbcLibraryRepository implements LibraryRepository {

    private static final RowMapper<LibraryAlbum> ENTRY = (row, number) -> {
        int rating = row.getInt("rating");
        Optional<Rating> knownRating = row.wasNull() ? Optional.empty() : Optional.of(new Rating(rating));
        return new LibraryAlbum(
                new LibraryAlbumId(row.getObject("id", UUID.class)),
                new PersonId(row.getObject("person_id", UUID.class)),
                AlbumColumns.read(row),
                knownRating,
                row.getString("notes"),
                new TreeSet<>(Columns.texts(row, "tags")),
                row.getDate("added_on").toLocalDate());
    };

    private final JdbcClient jdbc;

    JdbcLibraryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LibraryAlbum> findByPerson(PersonId person) {
        return jdbc.sql("select * from library_album where person_id = :person order by added_on, artist, title")
                .param("person", person.value())
                .query(ENTRY)
                .list();
    }

    @Override
    public Optional<LibraryAlbum> findById(LibraryAlbumId id) {
        return jdbc.sql("select * from library_album where id = :id").param("id", id.value()).query(ENTRY).optional();
    }

    @Override
    public void save(LibraryAlbum entry) {
        AlbumColumns.bind(jdbc.sql("""
                        insert into library_album (id, person_id, album_key, %s, rating, notes, tags, added_on)
                        values (:id, :person, :albumKey, %s, :rating, :notes, :tags, :addedOn)
                        on conflict (id) do update set album_key = excluded.album_key, artist = excluded.artist,
                            title = excluded.title, year = excluded.year, tidal_id = excluded.tidal_id,
                            spotify_id = excluded.spotify_id, musicbrainz_id = excluded.musicbrainz_id,
                            rating = excluded.rating, notes = excluded.notes, tags = excluded.tags"""
                        .formatted(AlbumColumns.NAMES, AlbumColumns.PARAMS)), entry.album())
                .param("id", entry.id().value())
                .param("person", entry.person().value())
                .param("rating", entry.rating().map(Rating::value).orElse(null))
                .param("notes", entry.notes())
                .param("tags", Columns.array(entry.tags()))
                .param("addedOn", entry.addedOn())
                .update();
    }

    @Override
    public void delete(LibraryAlbumId id) {
        jdbc.sql("delete from library_album where id = :id").param("id", id.value()).update();
    }
}
