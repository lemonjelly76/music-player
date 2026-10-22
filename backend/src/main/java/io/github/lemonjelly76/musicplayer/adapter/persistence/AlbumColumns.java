package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * The artist, title, year and catalogue id columns every album-holding table shares.
 */
final class AlbumColumns {

    static final String NAMES = "artist, title, year, tidal_id, spotify_id, musicbrainz_id, cover_url, formats";
    static final String PARAMS = ":artist, :title, :year, :tidalId, :spotifyId, :musicBrainzId, :coverUrl, :formats";

    private AlbumColumns() {
    }

    static StatementSpec bind(StatementSpec statement, AlbumRef album) {
        return statement
                .param("albumKey", album.key().value())
                .param("artist", album.artist())
                .param("title", album.title())
                .param("year", album.year().orElse(null))
                .param("tidalId", album.ids().tidal().orElse(null))
                .param("spotifyId", album.ids().spotify().orElse(null))
                .param("musicBrainzId", album.ids().musicBrainz().orElse(null))
                .param("coverUrl", album.cover().orElse(null))
                .param("formats", Columns.formats(album.formats()));
    }

    static AlbumRef read(ResultSet row) throws SQLException {
        int year = row.getInt("year");
        Optional<Integer> knownYear = row.wasNull() ? Optional.empty() : Optional.of(year);
        return new AlbumRef(row.getString("artist"), row.getString("title"), knownYear, new ProviderIds(
                Optional.ofNullable(row.getString("tidal_id")),
                Optional.ofNullable(row.getString("spotify_id")),
                Optional.ofNullable(row.getString("musicbrainz_id"))), Optional.ofNullable(row.getString("cover_url")),
                Columns.formats(row));
    }
}
