package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistCopy;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayBatch;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.application.World.NOW;
import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PlayServiceTest {

    private final World world = new World();
    private final Category running = world.categories.upsert("Running", "High energy");
    private final Category focus = world.categories.upsert("Focus", "Instrumental");

    private int collect(FakePorts.History... histories) {
        return new PlayService(world.playRepository, world.peopleRepository, world.playlistRepository,
                world.recommendationRepository, world.copyRepository, List.of(histories), world.clock).collect();
    }

    private Play play(String artist, String title, Duration ago, String playlist) {
        return new Play(album(artist, title), NOW.minus(ago), "spotify", Optional.ofNullable(playlist));
    }

    private void listOnPlaylist(Person person, Category category, String playlistId) {
        world.playlistRepository.save(Playlist.create(person.id(), Optional.of(category), "",
                List.of(new Track(Provider.SPOTIFY, "tr-1", "Daft Punk", "One More Time", "Discovery", Optional.empty())),
                NOW).writtenTo(1, new SyncedPlaylist(Provider.SPOTIFY, playlistId)));
    }

    private void albumListOnPlaylist(Person person, Category category, String playlistId) {
        world.recommendationRepository.save(RecommendationList.create(person.id(), Optional.of(category),
                List.of(Recommendation.create(album("Daft Punk", "Discovery"), "Fast", PickSource.POOL)), NOW)
                .syncedTo(new SyncedPlaylist(Provider.SPOTIFY, playlistId)));
    }

    @Test
    void playsFromAPlaylistWrittenFromAlbumsStillCountForItsActivity() {
        Person craig = world.withoutService("Craig");
        albumListOnPlaylist(craig, running, "old-playlist");
        FakePorts.History spotify = new FakePorts.History("spotify", cursor -> new PlayBatch(List.of(
                play("Daft Punk", "Discovery", Duration.ofHours(1), "old-playlist")), Optional.empty()));

        collect(spotify);

        assertThat(world.plays.stats(craig, Optional.of(running), 7).orElseThrow()).hasSize(1);
    }

    @Test
    void playsFromAnActivitysCopyOnAnotherServiceCountForTheActivity() {
        Person craig = world.tidalUser("Craig");
        world.copyRepository.save(PlaylistCopy.wanted(craig.id(), Optional.of(running.id()), Provider.SPOTIFY)
                .writtenAs("copy-playlist"));
        FakePorts.History spotify = new FakePorts.History("spotify", cursor -> new PlayBatch(List.of(
                play("Daft Punk", "Discovery", Duration.ofHours(1), "copy-playlist")), Optional.empty()));

        collect(spotify);

        assertThat(world.plays.stats(craig, Optional.of(running), 7).orElseThrow()).hasSize(1);
    }

    @Test
    void countsPlaysAndTiesThemToTheCategoryOfThePlaylistTheyCameFrom() {
        Person craig = world.withoutService("Craig");
        listOnPlaylist(craig, running, "run-playlist");
        FakePorts.History spotify = new FakePorts.History("spotify", cursor -> new PlayBatch(List.of(
                play("Daft Punk", "Discovery", Duration.ofHours(1), "run-playlist"),
                play("Daft Punk", "Discovery", Duration.ofHours(2), "run-playlist"),
                play("Air", "Moon Safari", Duration.ofHours(3), null)), Optional.of("cursor-2")));

        assertThat(collect(spotify)).isEqualTo(3);

        assertThat(world.plays.stats(craig, Optional.of(running), 7).orElseThrow())
                .extracting(AlbumPlays::plays).containsExactly(2);
        assertThat(world.plays.stats(craig, Optional.empty(), 7).orElseThrow())
                .extracting(total -> total.album().title(), AlbumPlays::plays)
                .containsExactly(tuple("Discovery", 2),
                        tuple("Moon Safari", 1));
        assertThat(world.playRepository.cursor(craig.id(), "spotify")).contains("cursor-2");
    }

    @Test
    void readsOnFromTheLastCursor() {
        world.withoutService("Craig");
        FakePorts.History history = new FakePorts.History("spotify", cursor -> PlayBatch.empty(Optional.of("next")));

        collect(history);
        collect(history);

        assertThat(history.cursorsAskedFor).containsExactly(Optional.empty(), Optional.of("next"));
    }

    @Test
    void keepsNothingFromACategoryWithCountingOff() {
        Person craig = world.withoutService("Craig");
        listOnPlaylist(craig, running, "run-playlist");
        world.plays.setTracking(craig, Optional.of(running), Optional.of(false));
        collect(new FakePorts.History("spotify", cursor -> new PlayBatch(List.of(
                play("Daft Punk", "Discovery", Duration.ofHours(1), "run-playlist"),
                play("Air", "Moon Safari", Duration.ofHours(3), null)), Optional.empty())));

        assertThat(world.plays.stats(craig, Optional.of(running), 7)).isEmpty();
        assertThat(world.plays.stats(craig, Optional.empty(), 7).orElseThrow())
                .extracting(total -> total.album().title()).containsExactly("Moon Safari");
    }

    @Test
    void readsNothingForSomeoneWithCountingOffEverywhere() {
        Person craig = world.withoutService("Craig");
        world.plays.setTracking(craig, Optional.empty(), Optional.of(false));
        FakePorts.History unread = new FakePorts.History("spotify", cursor -> {
            throw new AssertionError("should not be read");
        });

        assertThat(collect(unread)).isZero();
        assertThat(unread.cursorsAskedFor).isEmpty();
        assertThat(world.plays.stats(world.people.find("Craig"), Optional.empty(), 7)).isEmpty();
    }

    @Test
    void aCategoryCanBeCountedWhenThePersonIsNotAndCanFollowThePersonAgain() {
        world.plays.setTracking(world.withoutService("Craig"), Optional.empty(), Optional.of(false));
        Person off = world.people.find("Craig");

        world.plays.setTracking(off, Optional.of(focus), Optional.of(true));
        assertThat(world.plays.tracking(off).enabledFor(Optional.of(focus.id()))).isTrue();

        world.plays.setTracking(off, Optional.of(focus), Optional.empty());
        assertThat(world.plays.tracking(off).enabledFor(Optional.of(focus.id()))).isFalse();
    }

    @Test
    void aFailingHistoryDoesNotStopTheOthers() {
        world.withoutService("Craig");
        FakePorts.History down = new FakePorts.History("lastfm", cursor -> {
            throw new IllegalStateException("down");
        });
        FakePorts.History spotify = new FakePorts.History("spotify", cursor -> new PlayBatch(List.of(
                play("Air", "Moon Safari", Duration.ofHours(3), null)), Optional.empty()));

        assertThat(collect(down, spotify)).isEqualTo(1);
    }

    @Test
    void statsOnlyCoverTheGivenDays() {
        Person craig = world.withoutService("Craig");
        collect(new FakePorts.History("spotify", cursor -> new PlayBatch(List.of(
                play("Air", "Moon Safari", Duration.ofDays(10), null)), Optional.empty())));

        assertThat(world.plays.stats(craig, Optional.empty(), 7).orElseThrow()).isEmpty();
        assertThat(world.plays.stats(craig, Optional.empty(), 30).orElseThrow()).hasSize(1);
    }
}
