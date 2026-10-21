package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartPlaylist;
import io.github.lemonjelly76.musicplayer.domain.chart.ResolvedChart;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.ChartRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcChartRepository implements ChartRepository {

    private final JdbcClient jdbc;

    JdbcChartRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(ResolvedChart chart) {
        jdbc.sql("delete from chart_entry where provider = :provider").param("provider", chart.provider().name()).update();
        for (ResolvedChart.Song song : chart.songs()) {
            Track track = song.track();
            insert(chart, "SONG", song.position(), Optional.of(track.id()), track.artist(), track.title(), track.album(),
                    track.cover());
        }
        for (ResolvedChart.Album found : chart.albums()) {
            AlbumRef album = found.album();
            insert(chart, "ALBUM", found.position(), album.ids().get(chart.provider()), album.artist(), album.title(), "",
                    album.cover());
        }
    }

    private void insert(ResolvedChart chart, String kind, int position, Optional<String> itemId, String artist,
            String title, String album, Optional<String> cover) {
        jdbc.sql("""
                        insert into chart_entry (provider, kind, position, country, fetched_at, item_id, artist, title,
                            album, cover_url)
                        values (:provider, :kind, :position, :country, :fetchedAt, :itemId, :artist, :title, :album,
                            :cover)""")
                .param("provider", chart.provider().name())
                .param("kind", kind)
                .param("position", position)
                .param("country", chart.country())
                .param("fetchedAt", Columns.timestamp(chart.fetchedAt()))
                .param("itemId", itemId.orElse(null))
                .param("artist", artist)
                .param("title", title)
                .param("album", album)
                .param("cover", cover.orElse(null))
                .update();
    }

    @Override
    public Optional<ResolvedChart> find(Provider provider) {
        List<Row> rows = jdbc.sql("select * from chart_entry where provider = :provider order by kind desc, position")
                .param("provider", provider.name())
                .query((row, number) -> Row.read(row))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<ResolvedChart.Song> songs = new ArrayList<>();
        List<ResolvedChart.Album> albums = new ArrayList<>();
        for (Row row : rows) {
            if (row.kind().equals("SONG")) {
                songs.add(new ResolvedChart.Song(row.position(), new Track(provider, row.itemId(), row.artist(),
                        row.title(), row.album(), Optional.ofNullable(row.cover()))));
            } else {
                ProviderIds ids = row.itemId() == null ? ProviderIds.NONE : ProviderIds.of(provider, row.itemId());
                albums.add(new ResolvedChart.Album(row.position(), new AlbumRef(row.artist(), row.title(),
                        Optional.empty(), ids, Optional.ofNullable(row.cover()))));
            }
        }
        Row first = rows.getFirst();
        return Optional.of(new ResolvedChart(provider, first.country(), first.fetchedAt(), songs, albums));
    }

    @Override
    public ChartPlaylist playlistFor(PersonId person) {
        return jdbc.sql("select * from chart_playlist where person_id = :person")
                .param("person", person.value())
                .query((row, number) -> playlist(person, row))
                .optional()
                .orElseGet(() -> ChartPlaylist.wantedBy(person));
    }

    @Override
    public void save(ChartPlaylist playlist) {
        jdbc.sql("""
                        insert into chart_playlist (person_id, wanted, provider, playlist_id, written_at)
                        values (:person, :wanted, :provider, :playlistId, :writtenAt)
                        on conflict (person_id) do update set wanted = excluded.wanted, provider = excluded.provider,
                            playlist_id = excluded.playlist_id, written_at = excluded.written_at""")
                .param("person", playlist.person().value())
                .param("wanted", playlist.wanted())
                .param("provider", playlist.synced().map(synced -> synced.provider().name()).orElse(null))
                .param("playlistId", playlist.synced().map(SyncedPlaylist::playlistId).orElse(null))
                .param("writtenAt", playlist.writtenAt().map(Columns::timestamp).orElse(null))
                .update();
    }

    private static ChartPlaylist playlist(PersonId person, ResultSet row) throws SQLException {
        String playlistId = row.getString("playlist_id");
        Optional<SyncedPlaylist> synced = playlistId == null ? Optional.empty()
                : Optional.of(new SyncedPlaylist(Provider.valueOf(row.getString("provider")), playlistId));
        return new ChartPlaylist(person, row.getBoolean("wanted"), synced, Columns.instant(row, "written_at"));
    }

    private record Row(String kind, int position, String country, Instant fetchedAt, String itemId, String artist,
                       String title, String album, String cover) {

        static Row read(ResultSet row) throws SQLException {
            return new Row(row.getString("kind"), row.getInt("position"), row.getString("country"),
                    row.getTimestamp("fetched_at").toInstant(), row.getString("item_id"), row.getString("artist"),
                    row.getString("title"), row.getString("album"), row.getString("cover_url"));
        }
    }
}
