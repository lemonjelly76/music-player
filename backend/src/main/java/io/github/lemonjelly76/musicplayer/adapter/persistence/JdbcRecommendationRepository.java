package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.RecommendationRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationId;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationListId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcRecommendationRepository implements RecommendationRepository {

    private final JdbcClient jdbc;

    JdbcRecommendationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Saving a list again updates its playlist and its picks' ids and covers.
     */
    @Override
    @Transactional
    public void save(RecommendationList list) {
        jdbc.sql("""
                        insert into recommendation_list (id, person_id, category_id, name, created_at, provider,
                            provider_playlist_id)
                        values (:id, :person, :category, :name, :createdAt, :provider, :playlistId)
                        on conflict (id) do update set provider = excluded.provider,
                            provider_playlist_id = excluded.provider_playlist_id""")
                .param("id", list.id().value())
                .param("person", list.person().value())
                .param("category", list.category().map(CategoryId::value).orElse(null))
                .param("name", list.name())
                .param("createdAt", Columns.timestamp(list.createdAt()))
                .param("provider", list.playlist().map(synced -> synced.provider().name()).orElse(null))
                .param("playlistId", list.playlist().map(SyncedPlaylist::playlistId).orElse(null))
                .update();
        for (int position = 0; position < list.picks().size(); position++) {
            Recommendation pick = list.picks().get(position);
            AlbumColumns.bind(jdbc.sql("""
                            insert into recommendation (id, list_id, position, %s, reason, source)
                            values (:id, :list, :position, %s, :reason, :source)
                            on conflict (id) do update set tidal_id = excluded.tidal_id,
                                spotify_id = excluded.spotify_id, musicbrainz_id = excluded.musicbrainz_id,
                                cover_url = excluded.cover_url,
                                formats = coalesce(excluded.formats, recommendation.formats)"""
                            .formatted(AlbumColumns.NAMES, AlbumColumns.PARAMS)), pick.album())
                    .param("id", pick.id().value())
                    .param("list", list.id().value())
                    .param("position", position)
                    .param("reason", pick.reason())
                    .param("source", pick.source().name())
                    .update();
        }
    }

    @Override
    public Optional<RecommendationList> findById(RecommendationListId id) {
        return withPicks(jdbc.sql("select * from recommendation_list where id = :id")
                .param("id", id.value())
                .query((row, number) -> listWithoutPicks(row))
                .list()).stream().findFirst();
    }

    @Override
    public List<RecommendationList> findByPerson(PersonId person, Optional<CategoryId> category, int limit) {
        return withPicks(jdbc.sql("""
                        select * from recommendation_list
                        where person_id = :person
                            and (cast(:category as uuid) is null or category_id = cast(:category as uuid))
                        order by created_at desc
                        limit :limit""")
                .param("person", person.value())
                .param("category", category.map(CategoryId::value).orElse(null))
                .param("limit", limit)
                .query((row, number) -> listWithoutPicks(row))
                .list());
    }

    @Override
    public Optional<RecommendationList> findByPlaylist(String playlistId) {
        return withPicks(jdbc.sql("""
                        select * from recommendation_list where provider_playlist_id = :playlist
                        order by created_at desc limit 1""")
                .param("playlist", playlistId)
                .query((row, number) -> listWithoutPicks(row))
                .list()).stream().findFirst();
    }

    private List<RecommendationList> withPicks(List<RecommendationList> lists) {
        if (lists.isEmpty()) {
            return lists;
        }
        Map<UUID, List<Recommendation>> picksByList = new LinkedHashMap<>();
        jdbc.sql("select * from recommendation where list_id in (:lists) order by list_id, position")
                .param("lists", lists.stream().map(list -> list.id().value()).toList())
                .query((row, number) -> Map.entry(row.getObject("list_id", UUID.class), pick(row)))
                .list()
                .forEach(entry -> picksByList.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                        .add(entry.getValue()));
        return lists.stream()
                .map(list -> new RecommendationList(list.id(), list.person(), list.category(), list.name(),
                        list.createdAt(), list.playlist(), picksByList.getOrDefault(list.id().value(), List.of())))
                .toList();
    }

    private static RecommendationList listWithoutPicks(ResultSet row) throws SQLException {
        String playlistId = row.getString("provider_playlist_id");
        Optional<SyncedPlaylist> playlist = playlistId == null
                ? Optional.empty()
                : Optional.of(new SyncedPlaylist(Provider.valueOf(row.getString("provider")), playlistId));
        return new RecommendationList(
                new RecommendationListId(row.getObject("id", UUID.class)),
                new PersonId(row.getObject("person_id", UUID.class)),
                Columns.uuid(row, "category_id").map(CategoryId::new),
                row.getString("name"),
                row.getTimestamp("created_at").toInstant(),
                playlist,
                List.of());
    }

    private static Recommendation pick(ResultSet row) throws SQLException {
        return new Recommendation(
                new RecommendationId(row.getObject("id", UUID.class)),
                AlbumColumns.read(row),
                row.getString("reason"),
                PickSource.valueOf(row.getString("source")));
    }
}
