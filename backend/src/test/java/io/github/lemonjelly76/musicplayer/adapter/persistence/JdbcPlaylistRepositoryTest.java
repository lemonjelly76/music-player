package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.IntegrationTestConfiguration;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@Transactional
class JdbcPlaylistRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-21T18:00:00Z");

    @Autowired
    private PersonRepository people;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private PlaylistRepository playlists;

    private final Person craig = Person.create("Craig", Optional.of(Provider.SPOTIFY));
    private final Category running = Category.create("Running", "Fast");

    @BeforeEach
    void setUp() {
        people.save(craig);
        categories.save(running);
    }

    private Playlist playlist(Optional<Category> category, Instant at, String... titles) {
        List<Track> tracks = Arrays.stream(titles)
                .map(title -> new Track(Provider.SPOTIFY, "sp-" + title, "Artist", title, "Album",
                        Optional.of("https://covers/" + title)))
                .toList();
        return Playlist.create(craig.id(), category, "For " + titles.length, tracks, at);
    }

    @Test
    void keepsEachActivitysPlaylistsWithTheirSongsInOrder() {
        Playlist general = playlist(Optional.empty(), NOW.minus(Duration.ofDays(1)), "A", "B");
        Playlist older = playlist(Optional.of(running), NOW.minus(Duration.ofDays(7)), "C");
        Playlist newer = playlist(Optional.of(running), NOW, "D", "E", "F");

        playlists.save(general);
        playlists.save(older);
        playlists.save(newer);

        assertThat(playlists.latest(craig.id(), Optional.of(running.id()), 10)).containsExactly(newer, older);
        assertThat(playlists.latest(craig.id(), Optional.empty(), 10)).containsExactly(general);
        assertThat(playlists.latest(craig.id(), Optional.of(running.id()), 1)).containsExactly(newer);
    }

    @Test
    void savingASyncedPlaylistKeepsItsSongsAndFindsItByWhereItWasWritten() {
        Playlist saved = playlist(Optional.of(running), NOW, "A", "B");
        playlists.save(saved);

        Playlist synced = saved.writtenTo(2, new SyncedPlaylist(Provider.SPOTIFY, "pl-1"));
        playlists.save(synced);

        assertThat(playlists.findBySynced("pl-1")).contains(synced);
        assertThat(playlists.findBySynced("pl-2")).isEmpty();
    }

    @Test
    void findsAnActivitysPlaylistsForEveryoneAndSavesANewName() {
        Person robin = Person.create("Robin", Optional.empty());
        people.save(robin);
        Playlist craigs = playlist(Optional.of(running), NOW, "A");
        Playlist robins = Playlist.create(robin.id(), Optional.of(running), "", craigs.tracks(), NOW.minusSeconds(60));
        playlists.save(craigs);
        playlists.save(robins);
        playlists.save(playlist(Optional.empty(), NOW, "B"));

        playlists.save(craigs.renamed("AI Picks — Jogging"));

        assertThat(playlists.forActivity(running.id())).containsExactly(craigs.renamed("AI Picks — Jogging"), robins);
    }

    @Test
    void findsAPlaylistByIdAndThoseInTheAccountForAnActivity() {
        Playlist inAccount = playlist(Optional.of(running), NOW, "A")
                .writtenTo(3, new SyncedPlaylist(Provider.SPOTIFY, "pl-3"));
        Playlist replaced = playlist(Optional.of(running), NOW.minusSeconds(60), "B")
                .writtenTo(1, new SyncedPlaylist(Provider.SPOTIFY, "pl-1")).outOfAccount();
        Playlist general = playlist(Optional.empty(), NOW, "C").writtenTo(1, new SyncedPlaylist(Provider.SPOTIFY, "pl-g"));
        playlists.save(inAccount);
        playlists.save(replaced);
        playlists.save(general);

        assertThat(playlists.find(replaced.id())).contains(replaced);
        assertThat(playlists.find(PlaylistId.random())).isEmpty();
        assertThat(playlists.inAccount(craig.id(), Optional.of(running.id()))).containsExactly(inAccount);
        assertThat(playlists.inAccount(craig.id(), Optional.empty())).containsExactly(general);
    }
}
