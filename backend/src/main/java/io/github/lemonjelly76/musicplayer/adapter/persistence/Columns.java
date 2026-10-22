package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.AudioFormat;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * Formats as a comma-separated list of their names, or null for none.
     */
    static String formats(Set<AudioFormat> formats) {
        return formats.isEmpty() ? null : formats.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /**
     * The formats in the row's {@code formats} column; names no longer known are skipped.
     */
    static Set<AudioFormat> formats(ResultSet row) throws SQLException {
        String names = row.getString("formats");
        if (names == null || names.isBlank()) {
            return Set.of();
        }
        Set<String> known = Arrays.stream(AudioFormat.values()).map(Enum::name).collect(Collectors.toSet());
        return Arrays.stream(names.split(",")).map(String::strip).filter(known::contains).map(AudioFormat::valueOf)
                .collect(Collectors.toSet());
    }
}
