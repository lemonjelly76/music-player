package io.github.lemonjelly76.musicplayer.domain.playlist;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TrackMatchTest {

    private static Track track(String id, String artist, String title) {
        return new Track(Provider.SPOTIFY, id, artist, title, "Album", Optional.empty());
    }

    @Test
    void prefersTheExactSongOverAnEdition() {
        List<Track> found = List.of(track("1", "The Beatles", "Something - Remastered 2009"),
                track("2", "Beatles", "Something"));

        assertThat(TrackMatch.best(found, "The Beatles", "Something")).map(Track::id).contains("2");
    }

    @Test
    void takesAnEditionWhenThatIsAllThereIs() {
        List<Track> found = List.of(track("1", "The Beatles", "Something - Remastered 2009"));

        assertThat(TrackMatch.best(found, "Beatles", "Something")).map(Track::id).contains("1");
    }

    @Test
    void findsTheSongWhenTheArtistIsOneOfSeveral() {
        List<Track> found = List.of(track("1", "Daft Punk, Pharrell Williams, Nile Rodgers", "Get Lucky"));

        assertThat(TrackMatch.best(found, "Daft Punk", "Get Lucky")).isPresent();
    }

    @Test
    void ignoresAnotherArtistsSongOfTheSameName() {
        List<Track> found = List.of(track("1", "Air Supply", "All Out of Love"), track("2", "Air", "Sexy Boy"));

        assertThat(TrackMatch.best(found, "Air", "All Out of Love")).isEmpty();
    }

    @Test
    void prefersTheArtistsOwnRecordingToOneCreditingManyArtists() {
        List<Track> found = List.of(track("1", "Julius Waters, Kinobe, Fred Karger", "Slip into Something More Comfortable"),
                track("2", "Kinobe", "Slip into Something More Comfortable - Edit"));

        assertThat(TrackMatch.best(found, "Kinobe", "Slip Into Something More Comfortable")).map(Track::id).contains("2");
    }

    @Test
    void prefersTheOriginalWithAGuestToAnotherPerformance() {
        List<Track> found = List.of(track("1", "Air", "Playground Love - Demo Strings"),
                track("2", "Air", "Somersault - Live Sessions"),
                track("3", "Air, Gordon Tracks", "Playground Love"));

        assertThat(TrackMatch.best(found, "Air", "Playground Love")).map(Track::id).contains("3");
    }

    @Test
    void takesAnotherPerformanceWhenThatIsAllThereIs() {
        List<Track> found = List.of(track("1", "Zero 7", "Somersault - Live Sessions"));

        assertThat(TrackMatch.best(found, "Zero 7", "Somersault")).map(Track::id).contains("1");
    }
}
