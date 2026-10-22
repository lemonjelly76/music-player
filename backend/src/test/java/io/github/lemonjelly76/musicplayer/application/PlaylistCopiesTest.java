package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.PlaylistService.Saved;
import io.github.lemonjelly76.musicplayer.application.PlaylistService.TrackRequest;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistCopy;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.application.World.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PlaylistCopiesTest {

    private final World world = new World();
    private final Category running = world.categories.upsert("Running", "High energy");
    private Person craig;

    @BeforeEach
    void setUp() {
        craig = world.tidalUser("Craig");
        world.spotify.connectedPeople.add(craig.id());
        world.tidal.carryTrack("tr-1", "Air", "Sexy Boy", "Moon Safari");
        world.tidal.carryTrack("tr-2", "Made Up", "Only On Tidal", "Rare");
        world.spotify.carryTrack("sp-1", "Air", "Sexy Boy", "Moon Safari");
    }

    private Saved save(String... titles) {
        return world.playlists.save(craig, Optional.of(running), "Bright", List.of(titles).stream()
                .map(title -> new TrackRequest(title.equals("Sexy Boy") ? "Air" : "Made Up", title)).toList());
    }

    @Test
    void keepsTheActivitysNewestPlaylistOnTheOtherServiceUnderTheActivitysName() {
        world.copies.keep(craig, Optional.of(running), Provider.SPOTIFY);

        Saved first = save("Sexy Boy", "Only On Tidal");
        save("Sexy Boy");

        assertThat(first.sync()).isEqualTo(
                "synced to tidal as AI Picks — Running 1; copied to spotify as AI Picks — Running, 1 of 2 songs");
        assertThat(world.spotify.playlistCalls).containsExactly("AI Picks — Running [sp-1]", "AI Picks — Running [sp-1]");
        assertThat(world.spotify.deletedPlaylists).containsExactly("playlist-1");
        assertThat(world.copies.of(craig)).containsExactly(
                new PlaylistCopy(craig.id(), Optional.of(running.id()), Provider.SPOTIFY, Optional.of("playlist-2")));
    }

    @Test
    void copiesTheNewestPlaylistAsSoonAsACopyIsWanted() {
        save("Sexy Boy");

        String done = world.copies.keep(craig, Optional.of(running), Provider.SPOTIFY);

        assertThat(done).isEqualTo("copied to spotify as AI Picks — Running, 1 of 1 songs");
        assertThat(world.spotify.playlistCalls).containsExactly("AI Picks — Running [sp-1]");
    }

    @Test
    void takesThePlaceOfAPlaylistLeftThereUnderTheSameNameBeforeTheySwitched() {
        Playlist before = Playlist.create(craig.id(), Optional.of(running), "", List.of(world.spotify.songs.getFirst()),
                NOW.minusSeconds(60)).writtenTo(1, new SyncedPlaylist(Provider.SPOTIFY, "old-spotify"));
        world.playlistRepository.save(new Playlist(before.id(), before.person(), before.category(), before.name(), "",
                before.createdAt(), before.synced(), Optional.of(new Playlist.Slot(1, "AI Picks — Running")),
                before.tracks()));
        world.playlistRepository.save(Playlist.create(craig.id(), Optional.of(running), "",
                List.of(world.tidal.songs.getFirst()), NOW));

        world.copies.keep(craig, Optional.of(running), Provider.SPOTIFY);

        assertThat(world.spotify.deletedPlaylists).containsExactly("old-spotify");
        assertThat(world.playlistRepository.find(before.id()).orElseThrow().slot()).isEmpty();
    }

    @Test
    void aCopyThatFailsLeavesThePlaylistSavedAndSynced() {
        world.copies.keep(craig, Optional.of(running), Provider.SPOTIFY);
        world.spotify.failing = true;

        assertThat(save("Sexy Boy").sync())
                .isEqualTo("synced to tidal as AI Picks — Running 1; not copied to spotify: spotify stopped answering");
    }

    @Test
    void needsTheOtherServiceConnectedAndNotTheirOwn() {
        Person robin = world.tidalUser("Robin");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> world.copies.keep(robin, Optional.of(running), Provider.SPOTIFY))
                .withMessageContaining("Connect spotify");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> world.copies.keep(craig, Optional.of(running), Provider.TIDAL))
                .withMessageContaining("already");
    }

    @Test
    void stopsCopyingLeavingTheCopyThere() {
        world.copies.keep(craig, Optional.of(running), Provider.SPOTIFY);
        world.copies.stop(craig, Optional.of(running));

        save("Sexy Boy");

        assertThat(world.copies.of(craig)).isEmpty();
        assertThat(world.spotify.playlistCalls).isEmpty();
    }

    @Test
    void renamesTheCopyWhenItsActivityIsRenamed() {
        save("Sexy Boy");
        world.copies.keep(craig, Optional.of(running), Provider.SPOTIFY);

        world.playlists.renameFor(world.categories.rename("Running", "Jogging"));

        assertThat(world.spotify.renamedPlaylists).containsExactly("playlist-1 AI Picks — Jogging");
    }
}
