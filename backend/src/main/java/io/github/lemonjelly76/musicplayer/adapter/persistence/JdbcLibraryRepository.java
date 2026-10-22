package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbumId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.LibraryRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

@Repository
class JdbcLibraryRepository implements LibraryRepository {

    private final JdbcClient jdbc;

    JdbcLibraryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LibraryAlbum> findByPerson(PersonId person) {
        List<LibraryAlbum> entries = jdbc.sql(
                        "select * from library_album where person_id = :person order by added_on, artist, title")
                .param("person", person.value())
                .query((row, number) -> withoutRatings(row))
                .list();
        Map<UUID, List<ActivityRating>> ratings = new HashMap<>();
        jdbc.sql("""
                        select r.* from album_rating r join library_album a on a.id = r.library_album_id
                        where a.person_id = :person order by r.rated_at""")
                .param("person", person.value())
                .query((row, number) -> Map.entry(row.getObject("library_album_id", UUID.class), RatingColumns.read(row)))
                .list()
                .forEach(entry -> ratings.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(entry.getValue()));
        return entries.stream().map(entry -> withRatings(entry, ratings.getOrDefault(entry.id().value(), List.of())))
                .toList();
    }

    @Override
    public Optional<LibraryAlbum> findById(LibraryAlbumId id) {
        return jdbc.sql("select * from library_album where id = :id")
                .param("id", id.value())
                .query((row, number) -> withoutRatings(row))
                .optional()
                .map(entry -> withRatings(entry, jdbc.sql(
                                "select * from album_rating where library_album_id = :id order by rated_at")
                        .param("id", id.value())
                        .query((row, number) -> RatingColumns.read(row))
                        .list()));
    }

    /**
     * Saves the album and replaces its ratings with the ones it holds now.
     */
    @Override
    @Transactional
    public void save(LibraryAlbum entry) {
        AlbumColumns.bind(jdbc.sql("""
                        insert into library_album (id, person_id, album_key, %s, notes, tags, added_on)
                        values (:id, :person, :albumKey, %s, :notes, :tags, :addedOn)
                        on conflict (id) do update set album_key = excluded.album_key, artist = excluded.artist,
                            title = excluded.title, year = excluded.year, tidal_id = excluded.tidal_id,
                            spotify_id = excluded.spotify_id, musicbrainz_id = excluded.musicbrainz_id,
                            cover_url = excluded.cover_url, formats = coalesce(excluded.formats, library_album.formats),
                            notes = excluded.notes, tags = excluded.tags"""
                        .formatted(AlbumColumns.NAMES, AlbumColumns.PARAMS)), entry.album())
                .param("id", entry.id().value())
                .param("person", entry.person().value())
                .param("notes", entry.notes())
                .param("tags", Columns.array(entry.tags()))
                .param("addedOn", entry.addedOn())
                .update();
        jdbc.sql("delete from album_rating where library_album_id = :id").param("id", entry.id().value()).update();
        for (ActivityRating rating : entry.ratings()) {
            jdbc.sql("""
                            insert into album_rating (library_album_id, category_id, rating, comment, rated_at)
                            values (:id, :category, :rating, :comment, :ratedAt)""")
                    .param("id", entry.id().value())
                    .param("category", rating.category().map(CategoryId::value).orElse(null))
                    .param("rating", rating.rating().value())
                    .param("comment", rating.comment())
                    .param("ratedAt", Columns.timestamp(rating.ratedAt()))
                    .update();
        }
    }

    @Override
    public void delete(LibraryAlbumId id) {
        jdbc.sql("delete from library_album where id = :id").param("id", id.value()).update();
    }

    private static LibraryAlbum withoutRatings(ResultSet row) throws SQLException {
        return new LibraryAlbum(
                new LibraryAlbumId(row.getObject("id", UUID.class)),
                new PersonId(row.getObject("person_id", UUID.class)),
                AlbumColumns.read(row),
                List.of(),
                row.getString("notes"),
                new TreeSet<>(Columns.texts(row, "tags")),
                row.getDate("added_on").toLocalDate());
    }

    private static LibraryAlbum withRatings(LibraryAlbum entry, List<ActivityRating> ratings) {
        return new LibraryAlbum(entry.id(), entry.person(), entry.album(), ratings, entry.notes(), entry.tags(),
                entry.addedOn());
    }
}
