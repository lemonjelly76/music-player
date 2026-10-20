package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPersonRepository implements PersonRepository {

    private static final RowMapper<Person> PERSON = (row, number) -> new Person(
            new PersonId(row.getObject("id", UUID.class)),
            row.getString("name"),
            Optional.ofNullable(row.getString("provider")).map(Provider::valueOf),
            row.getBoolean("play_tracking"),
            Optional.ofNullable(row.getString("lastfm_user")));

    private final JdbcClient jdbc;

    JdbcPersonRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Person> findAll() {
        return jdbc.sql("select * from person order by name").query(PERSON).list();
    }

    @Override
    public Optional<Person> findById(PersonId id) {
        return jdbc.sql("select * from person where id = :id").param("id", id.value()).query(PERSON).optional();
    }

    @Override
    public void save(Person person) {
        jdbc.sql("""
                        insert into person (id, name, provider, play_tracking, lastfm_user)
                        values (:id, :name, :provider, :playTracking, :lastFmUser)
                        on conflict (id) do update set name = excluded.name, provider = excluded.provider,
                            play_tracking = excluded.play_tracking, lastfm_user = excluded.lastfm_user""")
                .param("id", person.id().value())
                .param("name", person.name())
                .param("provider", person.provider().map(Enum::name).orElse(null))
                .param("playTracking", person.playTracking())
                .param("lastFmUser", person.lastFmUser().orElse(null))
                .update();
    }
}
