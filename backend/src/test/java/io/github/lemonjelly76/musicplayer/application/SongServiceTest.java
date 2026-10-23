package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SongServiceTest {

    private final World world = new World();
    private final Category running = world.categories.upsert("Running", "");

    private static Track track(String id, String artist, String title) {
        return new Track(Provider.TIDAL, id, artist, title, "Album", Optional.empty());
    }

    @Test
    void ratingASongForAnActivityAddsItToTheLibraryOnce() {
        Person craig = world.tidalUser("Craig");

        world.songs.rate(craig, track("t1", "Air", "Sexy Boy"), Optional.of(running), Optional.of(Rating.OK));
        world.songs.rate(craig, track("t9", "Air", "Sexy Boy - Remastered"), Optional.of(running), Optional.of(Rating.LOVE));
        world.songs.rate(craig, track("t1", "Air", "Sexy Boy"), Optional.empty(), Optional.of(Rating.DISLIKE));

        assertThat(world.songRepository.findByPerson(craig.id())).singleElement().satisfies(song -> {
            assertThat(song.own(Optional.of(running.id()))).map(ActivityRating::rating).contains(Rating.LOVE);
            assertThat(song.own(Optional.empty())).map(ActivityRating::rating).contains(Rating.DISLIKE);
        });
    }

    @Test
    void takingAwayItsLastRatingTakesTheSongOutOfTheLibrary() {
        Person craig = world.tidalUser("Craig");
        world.songs.rate(craig, track("t1", "Air", "Sexy Boy"), Optional.of(running), Optional.of(Rating.LOVE));

        Optional<LibrarySong> left = world.songs.rate(craig, track("t1", "Air", "Sexy Boy"), Optional.of(running),
                Optional.empty());

        assertThat(left).isEmpty();
        assertThat(world.songRepository.findByPerson(craig.id())).isEmpty();
    }

    @Test
    void ratesASongNamedByArtistAndTitleOnceFoundOnTheService() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carryTrack("t1", "Air", "Sexy Boy", "Moon Safari");

        world.songs.rate(craig, "Air", "Sexy Boy", Optional.empty(), Optional.of(Rating.LOVE));

        assertThat(world.songRepository.findByPerson(craig.id())).extracting(song -> song.track().id()).containsExactly("t1");
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> world.songs.rate(craig, "Made Up", "Never Was", Optional.empty(), Optional.of(Rating.OK)))
                .withMessageContaining("Made Up — Never Was");
    }

    @Test
    void anActivityHasOnlyTheSongsRatedForItLovedFirst() {
        Person craig = world.tidalUser("Craig");
        world.songs.rate(craig, track("t1", "Air", "All I Need"), Optional.empty(), Optional.of(Rating.OK));
        world.songs.rate(craig, track("t2", "Air", "Sexy Boy"), Optional.of(running), Optional.of(Rating.LOVE));
        world.songs.rate(craig, track("t3", "Air", "Talisman"), Optional.of(running), Optional.of(Rating.DISLIKE));
        world.songs.rate(craig, track("t4", "Air", "Remember"), Optional.empty(), Optional.of(Rating.DISLIKE));

        assertThat(world.songs.songsFor(craig.id(), Optional.of(running.id())))
                .extracting(song -> song.track().title()).containsExactly("Sexy Boy", "Talisman");
        assertThat(world.songs.songsFor(craig.id(), Optional.empty()))
                .extracting(song -> song.track().title()).containsExactly("All I Need", "Remember");
        assertThat(world.songs.turnedDown(craig.id(), Optional.of(running.id())))
                .containsExactly("air|talisman");
    }

    @Test
    void searchesTheServiceForSongs() {
        Person craig = world.tidalUser("Craig");
        world.tidal.carryTrack("t1", "Air", "Sexy Boy", "Moon Safari");

        assertThat(world.songs.search(craig, "sexy", 10)).extracting(Track::id).containsExactly("t1");
        assertThatExceptionOfType(MusicProviderUnavailableException.class)
                .isThrownBy(() -> world.songs.search(world.withoutService("Robin"), "sexy", 10));
    }
}
