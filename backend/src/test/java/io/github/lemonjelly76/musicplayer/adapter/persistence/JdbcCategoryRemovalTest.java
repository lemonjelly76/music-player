package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.IntegrationTestConfiguration;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.LibraryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlayRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlayRepository.CategorisedPlay;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistRepository;
import io.github.lemonjelly76.musicplayer.domain.port.RecommendationRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Removing an activity takes what was only for it, rather than leaving its lists and playlists to turn up in General.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@Transactional
class JdbcCategoryRemovalTest {

    private static final Instant NOW = Instant.parse("2026-10-21T18:00:00Z");

    @Autowired
    private PersonRepository people;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private LibraryRepository library;
    @Autowired
    private RecommendationRepository recommendations;
    @Autowired
    private PlaylistRepository playlists;
    @Autowired
    private PlayRepository plays;

    private final Person craig = Person.create("Craig", Optional.of(Provider.SPOTIFY));
    private final Category explore = Category.create("Dinner Explore", "");
    private final Category running = Category.create("Running", "");

    private LibraryAlbum onlyForExplore;
    private LibraryAlbum alsoInGeneral;

    @BeforeEach
    void setUp() {
        people.save(craig);
        categories.save(explore);
        categories.save(running);
        onlyForExplore = LibraryAlbum.create(craig.id(), AlbumRef.of("Stan Getz", "Getz/Gilberto"),
                new ActivityRating(Optional.of(explore.id()), Rating.LOVE, "", NOW), "", List.of(), LocalDate.of(2026, 10, 21));
        alsoInGeneral = LibraryAlbum.create(craig.id(), AlbumRef.of("Air", "Moon Safari"),
                ActivityRating.general(Rating.LOVE, NOW), "", List.of(), LocalDate.of(2026, 10, 21))
                .rated(new ActivityRating(Optional.of(explore.id()), Rating.OK, "", NOW));
        library.save(onlyForExplore);
        library.save(alsoInGeneral);
        recommendations.save(RecommendationList.create(craig.id(), Optional.of(explore),
                List.of(Recommendation.create(AlbumRef.of("Céu", "Tropix"), "Warm", PickSource.POOL)), NOW));
        playlists.save(Playlist.create(craig.id(), Optional.of(explore), "", List.of(
                new Track(Provider.SPOTIFY, "t1", "Stan Getz", "The Girl From Ipanema", "Getz/Gilberto", Optional.empty())),
                NOW));
        plays.add(craig.id(), List.of(new CategorisedPlay(new Play(AlbumRef.of("Stan Getz", "Getz/Gilberto"), NOW,
                "spotify", Optional.of("pl")), Optional.of(explore.id()))));
    }

    @Test
    void takesTheActivitysListsPlaylistsAndRatingsWithIt() {
        categories.delete(explore.id());

        assertThat(categories.findAll()).containsExactly(running);
        assertThat(recommendations.findByPerson(craig.id(), Optional.empty(), 10)).isEmpty();
        assertThat(playlists.latest(craig.id(), Optional.empty(), 10)).isEmpty();
        assertThat(library.findByPerson(craig.id())).singleElement().satisfies(entry -> {
            assertThat(entry.album().title()).isEqualTo("Moon Safari");
            assertThat(entry.ratings()).extracting(ActivityRating::category).containsExactly(Optional.empty());
        });
    }

    @Test
    void keepsItsPlaysAsGeneralListening() {
        categories.delete(explore.id());

        assertThat(plays.albumTotals(craig.id(), Optional.empty(), NOW.minusSeconds(60)))
                .extracting(AlbumPlays::plays).containsExactly(1);
    }
}
