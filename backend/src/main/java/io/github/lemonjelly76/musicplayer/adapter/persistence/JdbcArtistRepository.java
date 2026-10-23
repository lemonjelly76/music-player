package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtist;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtistId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.ArtistRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcArtistRepository implements ArtistRepository {

    private final JdbcClient jdbc;

    JdbcArtistRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LibraryArtist> findByPerson(PersonId person) {
        Map<UUID, List<ActivityRating>> ratings = new HashMap<>();
        jdbc.sql("""
                        select r.* from artist_rating r join library_artist a on a.id = r.library_artist_id
                        where a.person_id = :person order by r.rated_at""")
                .param("person", person.value())
                .query((row, number) -> Map.entry(row.getObject("library_artist_id", UUID.class),
                        RatingColumns.read(row)))
                .list()
                .forEach(entry -> ratings.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                        .add(entry.getValue()));
        return jdbc.sql("select * from library_artist where person_id = :person order by name")
                .param("person", person.value())
                .query((row, number) -> {
                    UUID id = row.getObject("id", UUID.class);
                    return new LibraryArtist(new LibraryArtistId(id), person, row.getString("name"),
                            ratings.getOrDefault(id, List.of()), row.getDate("added_on").toLocalDate());
                })
                .list();
    }

    /**
     * Saves the artist and replaces their ratings with the ones they hold now.
     */
    @Override
    @Transactional
    public void save(LibraryArtist artist) {
        jdbc.sql("""
                        insert into library_artist (id, person_id, name, added_on)
                        values (:id, :person, :name, :addedOn)
                        on conflict (id) do update set name = excluded.name""")
                .param("id", artist.id().value())
                .param("person", artist.person().value())
                .param("name", artist.name())
                .param("addedOn", artist.addedOn())
                .update();
        jdbc.sql("delete from artist_rating where library_artist_id = :id").param("id", artist.id().value()).update();
        for (ActivityRating rating : artist.ratings()) {
            jdbc.sql("""
                            insert into artist_rating (library_artist_id, category_id, rating, comment, rated_at)
                            values (:id, :category, :rating, :comment, :ratedAt)""")
                    .param("id", artist.id().value())
                    .param("category", rating.category().map(CategoryId::value).orElse(null))
                    .param("rating", rating.rating().value())
                    .param("comment", rating.comment())
                    .param("ratedAt", Columns.timestamp(rating.ratedAt()))
                    .update();
        }
    }

    @Override
    public void delete(LibraryArtistId id) {
        jdbc.sql("delete from library_artist where id = :id").param("id", id.value()).update();
    }
}
