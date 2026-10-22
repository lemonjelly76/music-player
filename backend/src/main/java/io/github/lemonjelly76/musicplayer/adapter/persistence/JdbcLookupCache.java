package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.LookupCache;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
class JdbcLookupCache implements LookupCache {

    private final JdbcClient jdbc;

    JdbcLookupCache(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Lookup<Track>> track(Provider provider, String key) {
        return find(provider, "SONG", key).map(row -> new Lookup<>(row.itemId().map(id -> new Track(provider, id,
                row.artist(), row.title(), row.album(), row.cover())), row.at()));
    }

    @Override
    public void saveTrack(Provider provider, String key, Lookup<Track> lookup) {
        Optional<Track> track = lookup.found();
        save(provider, "SONG", key, new Row(lookup.at(), track.map(Track::id), track.map(Track::artist).orElse(null),
                track.map(Track::title).orElse(null), track.map(Track::album).orElse(null), Optional.empty(),
                track.flatMap(Track::cover)));
    }

    @Override
    public Optional<Lookup<AlbumRef>> album(Provider provider, String key) {
        return find(provider, "ALBUM", key).map(row -> new Lookup<>(row.itemId().map(id -> new AlbumRef(row.artist(),
                row.title(), row.year(), ProviderIds.of(provider, id), row.cover())), row.at()));
    }

    @Override
    public void saveAlbum(Provider provider, String key, Lookup<AlbumRef> lookup) {
        Optional<AlbumRef> album = lookup.found();
        save(provider, "ALBUM", key, new Row(lookup.at(), album.flatMap(found -> found.ids().get(provider)),
                album.map(AlbumRef::artist).orElse(null), album.map(AlbumRef::title).orElse(null), null,
                album.flatMap(AlbumRef::year), album.flatMap(AlbumRef::cover)));
    }

    private Optional<Row> find(Provider provider, String kind, String key) {
        return jdbc.sql("select * from catalogue_lookup where provider = :provider and kind = :kind and lookup_key = :key")
                .param("provider", provider.name())
                .param("kind", kind)
                .param("key", key)
                .query((row, number) -> Row.read(row))
                .optional();
    }

    private void save(Provider provider, String kind, String key, Row row) {
        jdbc.sql("""
                        insert into catalogue_lookup (provider, kind, lookup_key, looked_up_at, item_id, artist, title,
                            album, year, cover_url)
                        values (:provider, :kind, :key, :at, :itemId, :artist, :title, :album, :year, :cover)
                        on conflict (provider, kind, lookup_key) do update set looked_up_at = excluded.looked_up_at,
                            item_id = excluded.item_id, artist = excluded.artist, title = excluded.title,
                            album = excluded.album, year = excluded.year, cover_url = excluded.cover_url""")
                .param("provider", provider.name())
                .param("kind", kind)
                .param("key", key)
                .param("at", Columns.timestamp(row.at()))
                .param("itemId", row.itemId().orElse(null))
                .param("artist", row.artist())
                .param("title", row.title())
                .param("album", row.album())
                .param("year", row.year().orElse(null))
                .param("cover", row.cover().orElse(null))
                .update();
    }

    private record Row(Instant at, Optional<String> itemId, String artist, String title, String album,
                       Optional<Integer> year, Optional<String> cover) {

        static Row read(ResultSet row) throws SQLException {
            return new Row(row.getTimestamp("looked_up_at").toInstant(), Optional.ofNullable(row.getString("item_id")),
                    row.getString("artist"), row.getString("title"), row.getString("album"),
                    Optional.ofNullable(row.getObject("year", Integer.class)),
                    Optional.ofNullable(row.getString("cover_url")));
        }
    }
}
