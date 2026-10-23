package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.IntegrationTestConfiguration;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtist;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.ArtistRepository;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@Transactional
class JdbcArtistRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-23T18:00:00Z");

    @Autowired
    private PersonRepository people;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private ArtistRepository artists;

    @Test
    void keepsArtistsWithTheirRatingsPerActivityUntilTheirLastActivityGoes() {
        Person craig = Person.create("Craig", Optional.of(Provider.TIDAL));
        people.save(craig);
        Category running = Category.create("Running", "");
        categories.save(running);
        LibraryArtist air = LibraryArtist.create(craig.id(), "Air", ActivityRating.mainstream(Rating.LOVE, NOW),
                LocalDate.of(2026, 10, 23));
        LibraryArtist rerated = air.rated(new ActivityRating(Optional.of(running.id()), Rating.DISLIKE, "Too slow",
                NOW.plusSeconds(1)));
        LibraryArtist paramore = LibraryArtist.create(craig.id(), "Paramore",
                new ActivityRating(Optional.of(running.id()), Rating.DISLIKE, "", NOW), LocalDate.of(2026, 10, 23));

        artists.save(air);
        artists.save(rerated);
        artists.save(paramore);

        assertThat(artists.findByPerson(craig.id())).containsExactly(rerated, paramore);
        categories.delete(running.id());
        assertThat(artists.findByPerson(craig.id())).containsExactly(air);
        artists.delete(air.id());
        assertThat(artists.findByPerson(craig.id())).isEmpty();
    }
}
