package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.PlaylistService.Dropped;
import io.github.lemonjelly76.musicplayer.application.PlaylistService.Saved;
import io.github.lemonjelly76.musicplayer.application.PlaylistService.TrackRequest;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static io.github.lemonjelly76.musicplayer.application.World.NOW;
import static io.github.lemonjelly76.musicplayer.application.World.album;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PlaylistServiceTest {

    private final World world = new World();
    private final Category running = world.categories.upsert("Running", "High energy");

    private static TrackRequest song(String artist, String title) {
        return new TrackRequest(artist, title);
    }

    @Test
    void findsEachSongOnTheServiceAndWritesThemAsThePlaylist() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carryTrack("tr-1", "Daft Punk", "Harder, Better, Faster, Stronger", "Discovery");
        world.tidal.carryTrack("tr-2", "The Chemical Brothers", "Go", "Born in the Echoes");

        Saved saved = world.playlists.save(craig, Optional.of(running), "Bright and fast", List.of(
                song("Daft Punk", "Harder Better Faster Stronger"), song("Made Up", "Never Was"),
                song("Chemical Brothers", "Go")));

        assertThat(saved.playlist().name()).isEqualTo("AI Picks — Running");
        assertThat(saved.playlist().tracks()).extracting(Track::id).containsExactly("tr-1", "tr-2");
        assertThat(saved.dropped()).containsExactly(new Dropped("Made Up — Never Was", "not on tidal"));
        assertThat(saved.sync()).isEqualTo("synced to tidal as AI Picks — Running");
        assertThat(world.tidal.playlistCalls).containsExactly("AI Picks — Running [tr-1, tr-2]");
        assertThat(world.playlists.latest(craig, Optional.of(running.id()), 1)).singleElement()
                .satisfies(kept -> assertThat(kept.synced()).contains(new SyncedPlaylist(Provider.TIDAL, "playlist-1")));
    }

    @Test
    void keepsTheFirstFiftySongsOnceEach() {
        Person craig = world.tidalUser("Craig");
        IntStream.range(0, 60).forEach(n -> world.tidal.carryTrack("tr-" + n, "Artist", "Song " + n, "Album"));
        List<TrackRequest> requests = new ArrayList<>(List.of(song("Artist", "Song 0"), song("Artist", "Song 0")));
        IntStream.range(1, 60).forEach(n -> requests.add(song("Artist", "Song " + n)));

        Saved saved = world.playlists.save(craig, Optional.empty(), "", requests);

        assertThat(saved.playlist().tracks()).hasSize(Playlist.SIZE);
        assertThat(saved.dropped()).first().isEqualTo(new Dropped("Artist — Song 0", "already in this playlist"));
        assertThat(saved.dropped()).last().isEqualTo(new Dropped("Artist — Song 59", "not needed, the playlist is full"));
    }

    @Test
    void dropsSongsFromAlbumsTurnedDownForTheActivity() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carry("al-1", album("Portishead", "Dummy"));
        world.library.rate(craig, "tidal:al-1", Optional.of(running), Optional.of(Rating.DISLIKE), "");
        world.tidal.carryTrack("tr-1", "Portishead", "Glory Box", "Dummy");
        world.tidal.carryTrack("tr-2", "Air", "Sexy Boy", "Moon Safari");

        Saved saved = world.playlists.save(craig, Optional.of(running), "", List.of(
                song("Portishead", "Glory Box"), song("Air", "Sexy Boy")));

        assertThat(saved.dropped()).containsExactly(
                new Dropped("Portishead — Glory Box", "from Dummy, which they turned down for this"));
    }

    @Test
    void dropsSongsTurnedDownForTheActivity() {
        Person craig = world.tidalUser("Craig");
        Track glory = world.tidal.carryTrack("tr-1", "Portishead", "Glory Box", "Dummy");
        world.tidal.carryTrack("tr-2", "Air", "Sexy Boy", "Moon Safari");
        world.songs.rate(craig, glory, Optional.of(running), Optional.of(Rating.DISLIKE));

        Saved saved = world.playlists.save(craig, Optional.of(running), "", List.of(
                song("Portishead", "Glory Box"), song("Air", "Sexy Boy")));

        assertThat(saved.dropped()).containsExactly(
                new Dropped("Portishead — Glory Box", "they turned this song down for this"));
    }

    @Test
    void replacesTheActivitysPlaylistEvenWhenItWasMadeFromAlbums() {
        Person craig = world.tidalUser("Craig");
        world.recommendationRepository.save(RecommendationList.create(craig.id(), Optional.of(running),
                        List.of(Recommendation.create(album("Air", "Moon Safari"), "Calm", PickSource.POOL)), NOW)
                .syncedTo(new SyncedPlaylist(Provider.TIDAL, "album-playlist")));
        world.tidal.carryTrack("tr-1", "Air", "Sexy Boy", "Moon Safari");

        world.playlists.save(craig, Optional.of(running), "", List.of(song("Air", "Sexy Boy")));
        world.playlists.save(craig, Optional.of(running), "", List.of(song("Air", "Sexy Boy")));

        assertThat(world.tidal.deletedPlaylists).containsExactly("album-playlist", "playlist-1");
    }

    @Test
    void keepsThePlaylistWhenItCannotBeWritten() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carryTrack("tr-1", "Air", "Sexy Boy", "Moon Safari");
        world.tidal.connectedPeople.clear();

        Saved saved = world.playlists.save(craig, Optional.empty(), "", List.of(song("Air", "Sexy Boy")));

        assertThat(saved.sync()).isEqualTo("saved but not synced: tidal is not connected");
        assertThat(world.playlists.latest(craig, Optional.empty(), 5)).hasSize(1);

        world.tidal.connectedPeople.add(craig.id());
        assertThat(world.playlists.resync(craig, Optional.empty()).sync()).isEqualTo("synced to tidal as AI Picks");
    }

    @Test
    void needsAServiceToFindSongsOn() {
        Person craig = world.withoutService("Craig");

        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> world.playlists.save(craig, Optional.empty(), "", List.of(song("Air", "Sexy Boy"))))
                .withMessageContaining("Craig");
    }

    @Test
    void refusesAPlaylistWithNothingFound() {
        Person craig = world.tidalUser("Craig");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> world.playlists.save(craig, Optional.empty(), "", List.of(song("Made Up", "Never Was"))))
                .withMessageContaining("Made Up — Never Was");
    }

    @Test
    void generalPlaylistsAreKeptApartFromAnActivitys() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carryTrack("tr-1", "Air", "Sexy Boy", "Moon Safari");
        world.playlists.save(craig, Optional.of(running), "", List.of(song("Air", "Sexy Boy")));

        assertThat(world.playlists.latest(craig, Optional.empty(), 5)).isEmpty();
    }

    @Test
    void renamingAnActivityRenamesItsPlaylistsHereAndInTheAccount() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carryTrack("tr-1", "Air", "Sexy Boy", "Moon Safari");
        world.playlists.save(craig, Optional.of(running), "", List.of(song("Air", "Sexy Boy")));
        world.playlists.save(craig, Optional.of(running), "", List.of(song("Air", "Sexy Boy")));

        world.playlists.renameFor(world.categories.rename("Running", "Jogging"));

        assertThat(world.playlists.latest(craig, Optional.of(running.id()), 5))
                .extracting(Playlist::name).containsExactly("AI Picks — Jogging", "AI Picks — Jogging");
        assertThat(world.tidal.renamedPlaylists).containsExactly("playlist-2 AI Picks — Jogging");
    }

    @Test
    void renamesThePlaylistsHereEvenWhenTheAccountCannotBeReached() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carryTrack("tr-1", "Air", "Sexy Boy", "Moon Safari");
        world.playlists.save(craig, Optional.of(running), "", List.of(song("Air", "Sexy Boy")));
        world.tidal.connectedPeople.clear();

        world.playlists.renameFor(world.categories.rename("Running", "Jogging"));

        assertThat(world.playlists.latest(craig, Optional.of(running.id()), 1))
                .extracting(Playlist::name).containsExactly("AI Picks — Jogging");
    }
}
