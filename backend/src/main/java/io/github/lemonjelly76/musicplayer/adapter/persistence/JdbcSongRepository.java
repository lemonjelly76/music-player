package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySongId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.SongRepository;
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
class JdbcSongRepository implements SongRepository {

    private final JdbcClient jdbc;

    JdbcSongRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LibrarySong> findByPerson(PersonId person) {
        List<LibrarySong> songs = jdbc.sql(
                        "select * from library_song where person_id = :person order by added_on, artist, title")
                .param("person", person.value())
                .query((row, number) -> withoutRatings(row))
                .list();
        Map<UUID, List<ActivityRating>> ratings = new HashMap<>();
        jdbc.sql("""
                        select r.* from song_rating r join library_song s on s.id = r.library_song_id
                        where s.person_id = :person order by r.rated_at""")
                .param("person", person.value())
                .query((row, number) -> Map.entry(row.getObject("library_song_id", UUID.class), RatingColumns.read(row)))
                .list()
                .forEach(entry -> ratings.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(entry.getValue()));
        return songs.stream()
                .map(song -> new LibrarySong(song.id(), song.person(), song.track(),
                        ratings.getOrDefault(song.id().value(), List.of()), song.addedOn()))
                .toList();
    }

    /**
     * Saves the song, as last found, and replaces its ratings with the ones it holds now.
     */
    @Override
    @Transactional
    public void save(LibrarySong song) {
        Track track = song.track();
        jdbc.sql("""
                        insert into library_song (id, person_id, provider, track_id, artist, title, album, cover_url,
                            formats, added_on)
                        values (:id, :person, :provider, :trackId, :artist, :title, :album, :cover, :formats,
                            :addedOn)
                        on conflict (id) do update set provider = excluded.provider, track_id = excluded.track_id,
                            artist = excluded.artist, title = excluded.title, album = excluded.album,
                            cover_url = excluded.cover_url, formats = coalesce(excluded.formats, library_song.formats)""")
                .param("id", song.id().value())
                .param("person", song.person().value())
                .param("provider", track.provider().name())
                .param("trackId", track.id())
                .param("artist", track.artist())
                .param("title", track.title())
                .param("album", track.album())
                .param("cover", track.cover().orElse(null))
                .param("formats", Columns.formats(track.formats()))
                .param("addedOn", song.addedOn())
                .update();
        jdbc.sql("delete from song_rating where library_song_id = :id").param("id", song.id().value()).update();
        for (ActivityRating rating : song.ratings()) {
            jdbc.sql("""
                            insert into song_rating (library_song_id, category_id, rating, comment, rated_at)
                            values (:id, :category, :rating, :comment, :ratedAt)""")
                    .param("id", song.id().value())
                    .param("category", rating.category().map(CategoryId::value).orElse(null))
                    .param("rating", rating.rating().value())
                    .param("comment", rating.comment())
                    .param("ratedAt", Columns.timestamp(rating.ratedAt()))
                    .update();
        }
    }

    @Override
    public void delete(LibrarySongId id) {
        jdbc.sql("delete from library_song where id = :id").param("id", id.value()).update();
    }

    private static LibrarySong withoutRatings(ResultSet row) throws SQLException {
        return new LibrarySong(
                new LibrarySongId(row.getObject("id", UUID.class)),
                new PersonId(row.getObject("person_id", UUID.class)),
                new Track(Provider.valueOf(row.getString("provider")), row.getString("track_id"),
                        row.getString("artist"), row.getString("title"), row.getString("album"),
                        Optional.ofNullable(row.getString("cover_url")), Columns.formats(row)),
                List.of(),
                row.getDate("added_on").toLocalDate());
    }
}
