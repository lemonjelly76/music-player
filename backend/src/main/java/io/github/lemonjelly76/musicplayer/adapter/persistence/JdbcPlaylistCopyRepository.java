package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistCopy;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistCopyRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPlaylistCopyRepository implements PlaylistCopyRepository {

    private static final String SAME_ACTIVITY = """
            person_id = :person
                and category_id is not distinct from cast(:category as uuid)""";

    private final JdbcClient jdbc;

    JdbcPlaylistCopyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PlaylistCopy> find(PersonId person, Optional<CategoryId> activity) {
        return jdbc.sql("select * from playlist_copy where " + SAME_ACTIVITY)
                .param("person", person.value())
                .param("category", activity.map(CategoryId::value).orElse(null))
                .query((row, number) -> read(row))
                .optional();
    }

    @Override
    public List<PlaylistCopy> forPerson(PersonId person) {
        return jdbc.sql("select * from playlist_copy where person_id = :person")
                .param("person", person.value())
                .query((row, number) -> read(row))
                .list();
    }

    @Override
    public List<PlaylistCopy> forActivity(CategoryId activity) {
        return jdbc.sql("select * from playlist_copy where category_id = :category")
                .param("category", activity.value())
                .query((row, number) -> read(row))
                .list();
    }

    @Override
    public Optional<PlaylistCopy> findByPlaylist(String providerPlaylistId) {
        return jdbc.sql("select * from playlist_copy where provider_playlist_id = :playlist")
                .param("playlist", providerPlaylistId)
                .query((row, number) -> read(row))
                .optional();
    }

    @Override
    @Transactional
    public void save(PlaylistCopy copy) {
        delete(copy.person(), copy.category());
        jdbc.sql("""
                        insert into playlist_copy (person_id, category_id, provider, provider_playlist_id)
                        values (:person, :category, :provider, :playlist)""")
                .param("person", copy.person().value())
                .param("category", copy.category().map(CategoryId::value).orElse(null))
                .param("provider", copy.service().name())
                .param("playlist", copy.playlistId().orElse(null))
                .update();
    }

    @Override
    public void delete(PersonId person, Optional<CategoryId> activity) {
        jdbc.sql("delete from playlist_copy where " + SAME_ACTIVITY)
                .param("person", person.value())
                .param("category", activity.map(CategoryId::value).orElse(null))
                .update();
    }

    private static PlaylistCopy read(ResultSet row) throws SQLException {
        return new PlaylistCopy(new PersonId(row.getObject("person_id", UUID.class)),
                Columns.uuid(row, "category_id").map(CategoryId::new), Provider.valueOf(row.getString("provider")),
                Optional.ofNullable(row.getString("provider_playlist_id")));
    }
}
