package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.IntegrationTestConfiguration;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.LibraryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.port.TasteProfileRepository;
import io.github.lemonjelly76.musicplayer.domain.taste.TasteProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@Transactional
class JdbcPeopleAndLibraryTest {

    @Autowired
    private PersonRepository people;
    @Autowired
    private LibraryRepository library;
    @Autowired
    private TasteProfileRepository profiles;
    @Autowired
    private CategoryRepository categories;

    @Test
    void savesAndUpdatesPeople() {
        Person craig = Person.create("Craig", Optional.of(Provider.TIDAL));
        people.save(craig);
        Person changed = craig.withPlayTracking(false).withLastFmUser(Optional.of("craigfm"))
                .withProvider(Optional.of(Provider.SPOTIFY));

        people.save(changed);

        assertThat(people.findById(craig.id())).contains(changed);
        assertThat(people.findAll()).containsExactly(changed);
    }

    @Test
    void keepsLibraryAlbumsWithTheirIdsRatingAndTags() {
        Person craig = Person.create("Craig", Optional.empty());
        people.save(craig);
        AlbumRef blue = new AlbumRef("Joni Mitchell", "Blue", Optional.of(1971),
                new ProviderIds(Optional.of("t-1"), Optional.empty(), Optional.of("mb-1")));
        LibraryAlbum rated = LibraryAlbum.create(craig.id(), blue, Optional.of(new Rating(5)), "Perfect",
                List.of("folk", "70s"), LocalDate.of(2026, 10, 20));
        LibraryAlbum unrated = LibraryAlbum.create(craig.id(), AlbumRef.of("Air", "Moon Safari"), Optional.empty(), "",
                List.of(), LocalDate.of(2026, 10, 21));

        library.save(rated);
        library.save(unrated);
        library.save(rated.withNotes("Still perfect"));

        assertThat(library.findByPerson(craig.id())).containsExactly(rated.withNotes("Still perfect"), unrated);
        library.delete(unrated.id());
        assertThat(library.findById(unrated.id())).isEmpty();
    }

    @Test
    void keepsTasteProfilesAndCategories() {
        Person craig = Person.create("Craig", Optional.empty());
        people.save(craig);
        TasteProfile profile = TasteProfile.empty(craig.id()).rewritten("Warm soul", Optional.of(List.of("No jazz fusion")),
                Instant.parse("2026-10-20T18:00:00Z"));
        Category running = Category.create("Running", "Fast");

        profiles.save(profile);
        categories.save(running);
        categories.save(running.withRules("Fast and steady"));

        assertThat(profiles.find(craig.id())).contains(profile);
        assertThat(categories.findAll()).containsExactly(running.withRules("Fast and steady"));
    }
}
