package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reads a row of {@code album_rating} or {@code song_rating}, which share their columns.
 */
final class RatingColumns {

    private RatingColumns() {
    }

    static ActivityRating read(ResultSet row) throws SQLException {
        return new ActivityRating(
                Columns.uuid(row, "category_id").map(CategoryId::new),
                Rating.of(row.getInt("rating")),
                row.getString("comment"),
                row.getTimestamp("rated_at").toInstant());
    }
}
