package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.TasteProfileRepository;
import io.github.lemonjelly76.musicplayer.domain.taste.TasteProfile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JdbcTasteProfileRepository implements TasteProfileRepository {

    private final JdbcClient jdbc;

    JdbcTasteProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TasteProfile> find(PersonId person) {
        return jdbc.sql("select * from taste_profile where person_id = :person")
                .param("person", person.value())
                .query((row, number) -> new TasteProfile(person, row.getString("summary"),
                        Columns.texts(row, "never_recommend"), Columns.instant(row, "updated_at")))
                .optional();
    }

    @Override
    public void save(TasteProfile profile) {
        jdbc.sql("""
                        insert into taste_profile (person_id, summary, never_recommend, updated_at)
                        values (:person, :summary, :neverRecommend, :updatedAt)
                        on conflict (person_id) do update set summary = excluded.summary,
                            never_recommend = excluded.never_recommend, updated_at = excluded.updated_at""")
                .param("person", profile.person().value())
                .param("summary", profile.summary())
                .param("neverRecommend", Columns.array(profile.neverRecommend()))
                .param("updatedAt", profile.updatedAt().map(Columns::timestamp).orElse(null))
                .update();
    }
}
