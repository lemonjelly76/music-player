package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.IntegrationTestConfiguration;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlayRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlayRepository.CategorisedPlay;
import io.github.lemonjelly76.musicplayer.domain.port.RecommendationRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@Transactional
class JdbcRecommendationsAndPlaysTest {

    private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");

    @Autowired
    private PersonRepository people;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private RecommendationRepository recommendations;
    @Autowired
    private PlayRepository plays;

    private final Person craig = Person.create("Craig", Optional.of(Provider.TIDAL));
    private final Category running = Category.create("Running", "Fast");

    @BeforeEach
    void setUp() {
        people.save(craig);
        categories.save(running);
    }

    private RecommendationList list(Optional<Category> category, Instant at, String... titles) {
        List<Recommendation> picks = Arrays.stream(titles)
                .map(title -> Recommendation.create(new AlbumRef("Artist", title, Optional.of(1999),
                        ProviderIds.of(Provider.TIDAL, "t-" + title)), "Because " + title, PickSource.POOL))
                .toList();
        return RecommendationList.create(craig.id(), category, picks, at);
    }

    @Test
    void keepsListsWithTheirPicksInOrderNewestFirst() {
        RecommendationList older = list(Optional.empty(), NOW.minus(Duration.ofDays(7)), "A", "B");
        RecommendationList newer = list(Optional.of(running), NOW, "C", "D", "E");

        recommendations.save(older);
        recommendations.save(newer);

        assertThat(recommendations.findByPerson(craig.id(), Optional.empty(), 10)).containsExactly(newer, older);
        assertThat(recommendations.findByPerson(craig.id(), Optional.of(running.id()), 10)).containsExactly(newer);
        assertThat(recommendations.findByPerson(craig.id(), Optional.empty(), 1)).containsExactly(newer);
        assertThat(recommendations.findById(older.id())).contains(older);
    }

    @Test
    void savingASyncedListKeepsItsPicksAndFindsItByPlaylist() {
        RecommendationList saved = list(Optional.of(running), NOW, "A", "B");
        recommendations.save(saved);

        recommendations.save(saved.syncedTo(new SyncedPlaylist(Provider.TIDAL, "pl-1")));

        RecommendationList found = recommendations.findByPlaylist("pl-1").orElseThrow();
        assertThat(found.picks()).isEqualTo(saved.picks());
        assertThat(found.playlist()).contains(new SyncedPlaylist(Provider.TIDAL, "pl-1"));
        assertThat(recommendations.findByPlaylist("pl-2")).isEmpty();
    }

    @Test
    void rollsPlaysUpToAlbumsAndIgnoresRepeats() {
        AlbumRef discovery = AlbumRef.of("Daft Punk", "Discovery");
        AlbumRef safari = AlbumRef.of("Air", "Moon Safari");
        plays.add(craig.id(), List.of(
                played(discovery, 1, Optional.of(running)),
                played(discovery, 2, Optional.of(running)),
                played(safari, 3, Optional.empty()),
                played(safari, 400, Optional.empty())));
        plays.add(craig.id(), List.of(played(discovery, 1, Optional.of(running))));

        assertThat(plays.albumTotals(craig.id(), Optional.empty(), NOW.minus(Duration.ofDays(1))))
                .extracting(total -> total.album().title(), AlbumPlays::plays)
                .containsExactly(tuple("Discovery", 2), tuple("Moon Safari", 1));
        assertThat(plays.albumTotals(craig.id(), Optional.of(running.id()), NOW.minus(Duration.ofDays(30))))
                .extracting(AlbumPlays::plays).containsExactly(2);

        plays.clear(craig.id(), Optional.of(running.id()));
        assertThat(plays.albumTotals(craig.id(), Optional.empty(), NOW.minus(Duration.ofDays(30))))
                .extracting(AlbumPlays::plays).containsExactly(2);
    }

    @Test
    void keepsOverridesAndCursors() {
        plays.setTrackingOverride(craig.id(), running.id(), Optional.of(false));
        assertThat(plays.trackingOverrides(craig.id())).isEqualTo(Map.of(running.id(), false));
        plays.setTrackingOverride(craig.id(), running.id(), Optional.of(true));
        assertThat(plays.trackingOverrides(craig.id())).isEqualTo(Map.of(running.id(), true));
        plays.setTrackingOverride(craig.id(), running.id(), Optional.empty());
        assertThat(plays.trackingOverrides(craig.id())).isEmpty();

        plays.saveCursor(craig.id(), "spotify", "1");
        plays.saveCursor(craig.id(), "spotify", "2");
        assertThat(plays.cursor(craig.id(), "spotify")).contains("2");
        assertThat(plays.cursor(craig.id(), "lastfm")).isEmpty();
    }

    private static CategorisedPlay played(AlbumRef album, int hoursAgo, Optional<Category> category) {
        return new CategorisedPlay(new Play(album, NOW.minus(Duration.ofHours(hoursAgo)), "spotify", Optional.empty()),
                category.map(Category::id));
    }
}
