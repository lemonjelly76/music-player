package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcCategoryRepository implements CategoryRepository {

    private static final RowMapper<Category> CATEGORY = (row, number) -> new Category(
            new CategoryId(row.getObject("id", UUID.class)), row.getString("name"), row.getString("rules"));

    private final JdbcClient jdbc;

    JdbcCategoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Category> findAll() {
        return jdbc.sql("select * from category order by position, name").query(CATEGORY).list();
    }

    @Override
    public Optional<Category> findById(CategoryId id) {
        return jdbc.sql("select * from category where id = :id").param("id", id.value()).query(CATEGORY).optional();
    }

    @Override
    public void save(Category category) {
        jdbc.sql("""
                        insert into category (id, name, rules, position)
                        values (:id, :name, :rules, (select coalesce(max(position), 0) + 1 from category))
                        on conflict (id) do update set name = excluded.name, rules = excluded.rules""")
                .param("id", category.id().value())
                .param("name", category.name())
                .param("rules", category.rules())
                .update();
    }

    @Override
    @Transactional
    public void reorder(List<CategoryId> order) {
        for (int position = 0; position < order.size(); position++) {
            jdbc.sql("update category set position = :position where id = :id")
                    .param("position", position + 1)
                    .param("id", order.get(position).value())
                    .update();
        }
    }

    /**
     * Ratings, lists and playlists go with the category through the schema's cascades. An album, song or artist
     * with no rating left leaves the library, as it does when its last rating is taken away.
     */
    @Override
    @Transactional
    public void delete(CategoryId id) {
        jdbc.sql("delete from category where id = :id").param("id", id.value()).update();
        jdbc.sql("""
                        delete from library_album album
                        where not exists (select 1 from album_rating rating where rating.library_album_id = album.id)""")
                .update();
        jdbc.sql("""
                        delete from library_song song
                        where not exists (select 1 from song_rating rating where rating.library_song_id = song.id)""")
                .update();
        jdbc.sql("""
                        delete from library_artist artist
                        where not exists (select 1 from artist_rating rating where rating.library_artist_id = artist.id)""")
                .update();
    }
}
