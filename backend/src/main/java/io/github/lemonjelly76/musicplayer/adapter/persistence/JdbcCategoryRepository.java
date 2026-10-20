package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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
        return jdbc.sql("select * from category order by name").query(CATEGORY).list();
    }

    @Override
    public Optional<Category> findById(CategoryId id) {
        return jdbc.sql("select * from category where id = :id").param("id", id.value()).query(CATEGORY).optional();
    }

    @Override
    public void save(Category category) {
        jdbc.sql("""
                        insert into category (id, name, rules) values (:id, :name, :rules)
                        on conflict (id) do update set name = excluded.name, rules = excluded.rules""")
                .param("id", category.id().value())
                .param("name", category.name())
                .param("rules", category.rules())
                .update();
    }
}
