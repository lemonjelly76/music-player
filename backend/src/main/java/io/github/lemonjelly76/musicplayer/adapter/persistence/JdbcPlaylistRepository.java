package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist.Slot;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
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
import java.util.UUID;

@Repository
class JdbcPlaylistRepository implements PlaylistRepository {

    private final JdbcClient jdbc;

    JdbcPlaylistRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(Playlist playlist) {
        jdbc.sql("""
                        insert into playlist (id, person_id, category_id, name, description, created_at, provider,
                            provider_playlist_id, slot, name_in_account)
                        values (:id, :person, :category, :name, :description, :createdAt, :provider, :synced, :slot,
                            :nameInAccount)
                        on conflict (id) do update set name = excluded.name, provider = excluded.provider,
                            provider_playlist_id = excluded.provider_playlist_id, slot = excluded.slot,
                            name_in_account = excluded.name_in_account""")
                .param("id", playlist.id().value())
                .param("person", playlist.person().value())
                .param("category", playlist.category().map(CategoryId::value).orElse(null))
                .param("name", playlist.name())
                .param("description", playlist.description())
                .param("createdAt", Columns.timestamp(playlist.createdAt()))
                .param("provider", playlist.synced().map(synced -> synced.provider().name()).orElse(null))
                .param("synced", playlist.synced().map(SyncedPlaylist::playlistId).orElse(null))
                .param("slot", playlist.slot().map(Slot::number).orElse(null))
                .param("nameInAccount", playlist.slot().map(Slot::nameInAccount).orElse(null))
                .update();
        jdbc.sql("delete from playlist_track where playlist_id = :playlist")
                .param("playlist", playlist.id().value())
                .update();
        for (int position = 0; position < playlist.tracks().size(); position++) {
            Track track = playlist.tracks().get(position);
            jdbc.sql("""
                            insert into playlist_track (playlist_id, position, provider, track_id, artist, title, album,
                                cover_url, formats)
                            values (:playlist, :position, :provider, :trackId, :artist, :title, :album, :cover,
                                :formats)""")
                    .param("playlist", playlist.id().value())
                    .param("position", position)
                    .param("provider", track.provider().name())
                    .param("trackId", track.id())
                    .param("artist", track.artist())
                    .param("title", track.title())
                    .param("album", track.album())
                    .param("cover", track.cover().orElse(null))
                    .param("formats", Columns.formats(track.formats()))
                    .update();
        }
    }

    @Override
    public List<Playlist> latest(PersonId person, Optional<CategoryId> activity, int limit) {
        return withTracks(jdbc.sql("""
                        select * from playlist
                        where person_id = :person and category_id is not distinct from cast(:category as uuid)
                        order by created_at desc
                        limit :limit""")
                .param("person", person.value())
                .param("category", activity.map(CategoryId::value).orElse(null))
                .param("limit", limit)
                .query((row, number) -> withoutTracks(row))
                .list());
    }

    @Override
    public Optional<Playlist> find(PlaylistId id) {
        return withTracks(jdbc.sql("select * from playlist where id = :id")
                .param("id", id.value())
                .query((row, number) -> withoutTracks(row))
                .list()).stream().findFirst();
    }

    @Override
    public List<Playlist> inAccount(PersonId person, Optional<CategoryId> activity) {
        return withTracks(jdbc.sql("""
                        select * from playlist
                        where person_id = :person and category_id is not distinct from cast(:category as uuid)
                            and slot is not null
                        order by created_at desc""")
                .param("person", person.value())
                .param("category", activity.map(CategoryId::value).orElse(null))
                .query((row, number) -> withoutTracks(row))
                .list());
    }

    @Override
    public List<Playlist> forActivity(CategoryId activity) {
        return withTracks(jdbc.sql("select * from playlist where category_id = :category order by created_at desc")
                .param("category", activity.value())
                .query((row, number) -> withoutTracks(row))
                .list());
    }

    @Override
    public Optional<Playlist> findBySynced(String providerPlaylistId) {
        return withTracks(jdbc.sql("""
                        select * from playlist where provider_playlist_id = :synced
                        order by created_at desc limit 1""")
                .param("synced", providerPlaylistId)
                .query((row, number) -> withoutTracks(row))
                .list()).stream().findFirst();
    }

    private List<Playlist> withTracks(List<Playlist> playlists) {
        if (playlists.isEmpty()) {
            return playlists;
        }
        Map<UUID, List<Track>> tracks = new HashMap<>();
        jdbc.sql("select * from playlist_track where playlist_id in (:playlists) order by playlist_id, position")
                .param("playlists", playlists.stream().map(playlist -> playlist.id().value()).toList())
                .query((row, number) -> Map.entry(row.getObject("playlist_id", UUID.class), track(row)))
                .list()
                .forEach(entry -> tracks.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                        .add(entry.getValue()));
        return playlists.stream()
                .map(playlist -> new Playlist(playlist.id(), playlist.person(), playlist.category(), playlist.name(),
                        playlist.description(), playlist.createdAt(), playlist.synced(), playlist.slot(),
                        tracks.getOrDefault(playlist.id().value(), List.of())))
                .toList();
    }

    private static Playlist withoutTracks(ResultSet row) throws SQLException {
        String synced = row.getString("provider_playlist_id");
        Integer slot = row.getObject("slot", Integer.class);
        return new Playlist(
                new PlaylistId(row.getObject("id", UUID.class)),
                new PersonId(row.getObject("person_id", UUID.class)),
                Columns.uuid(row, "category_id").map(CategoryId::new),
                row.getString("name"),
                row.getString("description"),
                row.getTimestamp("created_at").toInstant(),
                synced == null ? Optional.empty()
                        : Optional.of(new SyncedPlaylist(Provider.valueOf(row.getString("provider")), synced)),
                slot == null ? Optional.empty() : Optional.of(new Slot(slot, row.getString("name_in_account"))),
                List.of());
    }

    private static Track track(ResultSet row) throws SQLException {
        return new Track(Provider.valueOf(row.getString("provider")), row.getString("track_id"),
                row.getString("artist"), row.getString("title"), row.getString("album"),
                Optional.ofNullable(row.getString("cover_url")), Columns.formats(row));
    }
}
