package io.github.lemonjelly76.musicplayer.adapter.persistence;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class Columns {

    private Columns() {
    }

    static List<String> texts(ResultSet row, String column) throws SQLException {
        Array array = row.getArray(column);
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    static String[] array(Collection<String> values) {
        return values.toArray(String[]::new);
    }

    static Optional<UUID> uuid(ResultSet row, String column) throws SQLException {
        return Optional.ofNullable(row.getObject(column, UUID.class));
    }

    static Optional<Instant> instant(ResultSet row, String column) throws SQLException {
        return Optional.ofNullable(row.getTimestamp(column)).map(Timestamp::toInstant);
    }

    static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
