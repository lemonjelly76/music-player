package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.IntegrationTestConfiguration;
import io.github.lemonjelly76.musicplayer.domain.album.AudioFormat;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.port.SongRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@Transactional
class JdbcSongRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-21T18:00:00Z");

    @Autowired
    private PersonRepository people;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private SongRepository songs;

    @Test
    void keepsSongsWithTheirRatingsPerActivityAndForgetsThemWhenRemoved() {
        Person craig = Person.create("Craig", Optional.of(Provider.SPOTIFY));
        people.save(craig);
        Category running = Category.create("Running", "");
        categories.save(running);
        LibrarySong song = LibrarySong.create(craig.id(), new Track(Provider.SPOTIFY, "t1", "Air", "Sexy Boy",
                "Moon Safari", Optional.of("https://i/300"), Set.of(AudioFormat.HI_RES)), ActivityRating.mainstream(Rating.LOVE, NOW),
                LocalDate.of(2026, 10, 21));
        LibrarySong rerated = song.rated(new ActivityRating(Optional.of(running.id()), Rating.DISLIKE, "", NOW.plusSeconds(1)));

        songs.save(song);
        songs.save(rerated);

        assertThat(songs.findByPerson(craig.id())).containsExactly(rerated);
        songs.delete(song.id());
        assertThat(songs.findByPerson(craig.id())).isEmpty();
    }
}
