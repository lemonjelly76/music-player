package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;
import io.github.lemonjelly76.musicplayer.domain.port.PlayRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
class JdbcPlayRepository implements PlayRepository {

    private final JdbcClient jdbc;

    JdbcPlayRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void add(PersonId person, Collection<CategorisedPlay> plays) {
        for (CategorisedPlay categorised : plays) {
            Play play = categorised.play();
            AlbumColumns.bind(jdbc.sql("""
                            insert into play (person_id, source, played_at, album_key, %s, category_id)
                            values (:person, :source, :playedAt, :albumKey, %s, :category)
                            on conflict do nothing""".formatted(AlbumColumns.NAMES, AlbumColumns.PARAMS)), play.album())
                    .param("person", person.value())
                    .param("source", play.source())
                    .param("playedAt", Columns.timestamp(play.playedAt()))
                    .param("category", categorised.category().map(CategoryId::value).orElse(null))
                    .update();
        }
    }

    /**
     * Names and ids come from the album's most recent play, so a later, better-identified play wins.
     */
    @Override
    public List<AlbumPlays> albumTotals(PersonId person, Optional<CategoryId> category, Instant since) {
        return jdbc.sql("""
                        select distinct on (album_key) album_key, artist, title, year, tidal_id, spotify_id,
                            musicbrainz_id, cover_url,
                            count(*) over (partition by album_key) as plays,
                            max(played_at) over (partition by album_key) as last_played
                        from play
                        where person_id = :person and played_at >= :since
                            and (cast(:category as uuid) is null or category_id = cast(:category as uuid))
                        order by album_key, played_at desc""")
                .param("person", person.value())
                .param("since", Columns.timestamp(since))
                .param("category", category.map(CategoryId::value).orElse(null))
                .query((row, number) -> new AlbumPlays(AlbumColumns.read(row), row.getInt("plays"),
                        row.getTimestamp("last_played").toInstant()))
                .list()
                .stream()
                .sorted((left, right) -> left.plays() != right.plays()
                        ? Integer.compare(right.plays(), left.plays())
                        : right.lastPlayed().compareTo(left.lastPlayed()))
                .toList();
    }

    @Override
    public void clear(PersonId person, Optional<CategoryId> category) {
        jdbc.sql("""
                        delete from play where person_id = :person
                            and (cast(:category as uuid) is null or category_id = cast(:category as uuid))""")
                .param("person", person.value())
                .param("category", category.map(CategoryId::value).orElse(null))
                .update();
    }

    @Override
    public Map<CategoryId, Boolean> trackingOverrides(PersonId person) {
        return jdbc.sql("select category_id, enabled from play_tracking_override where person_id = :person")
                .param("person", person.value())
                .query((row, number) -> Map.entry(new CategoryId(row.getObject("category_id", UUID.class)),
                        row.getBoolean("enabled")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void setTrackingOverride(PersonId person, CategoryId category, Optional<Boolean> enabled) {
        if (enabled.isEmpty()) {
            jdbc.sql("delete from play_tracking_override where person_id = :person and category_id = :category")
                    .param("person", person.value())
                    .param("category", category.value())
                    .update();
            return;
        }
        jdbc.sql("""
                        insert into play_tracking_override (person_id, category_id, enabled)
                        values (:person, :category, :enabled)
                        on conflict (person_id, category_id) do update set enabled = excluded.enabled""")
                .param("person", person.value())
                .param("category", category.value())
                .param("enabled", enabled.get())
                .update();
    }

    @Override
    public Optional<String> cursor(PersonId person, String source) {
        return jdbc.sql("select cursor from listening_cursor where person_id = :person and source = :source")
                .param("person", person.value())
                .param("source", source)
                .query(String.class)
                .optional();
    }

    @Override
    public void saveCursor(PersonId person, String source, String cursor) {
        jdbc.sql("""
                        insert into listening_cursor (person_id, source, cursor) values (:person, :source, :cursor)
                        on conflict (person_id, source) do update set cursor = excluded.cursor""")
                .param("person", person.value())
                .param("source", source)
                .param("cursor", cursor)
                .update();
    }
}
