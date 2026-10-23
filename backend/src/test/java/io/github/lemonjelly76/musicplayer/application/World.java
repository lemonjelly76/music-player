package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.album.ProviderIds;
import io.github.lemonjelly76.musicplayer.domain.person.Person;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Every application service wired to in-memory ports, at a fixed moment.
 */
final class World {

    static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");

    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final FakePorts.People peopleRepository = new FakePorts.People();
    final FakePorts.Library libraryRepository = new FakePorts.Library();
    final FakePorts.Categories categoryRepository = new FakePorts.Categories();
    final FakePorts.Plays playRepository = new FakePorts.Plays();
    final FakePorts.Recommendations recommendationRepository = new FakePorts.Recommendations();
    final FakePorts.Playlists playlistRepository = new FakePorts.Playlists();
    final FakePorts.Songs songRepository = new FakePorts.Songs();
    final FakePorts.Copies copyRepository = new FakePorts.Copies();
    final FakePorts.Artists artistRepository = new FakePorts.Artists();
    final FakePorts.FakeProvider tidal = new FakePorts.FakeProvider(Provider.TIDAL);
    final FakePorts.FakeProvider spotify = new FakePorts.FakeProvider(Provider.SPOTIFY);
    final FakePorts.Catalogue catalogue = new FakePorts.Catalogue();
    final FakePorts.Similarity lastFm = new FakePorts.Similarity("lastfm");

    final PersonService people = new PersonService(peopleRepository);
    final CategoryService categories = new CategoryService(categoryRepository);
    final MusicProviders providers = new MusicProviders(List.of(tidal, spotify));
    final AlbumResolver resolver = new AlbumResolver(providers, catalogue);
    final LibraryService library = new LibraryService(libraryRepository, resolver, providers, clock);
    final ArtistService artists = new ArtistService(artistRepository, clock);
    final RecommendationService recommendations = new RecommendationService(recommendationRepository, resolver,
            library, artists, providers, clock);
    final SongService songs = new SongService(songRepository, providers, clock);
    final PlaylistCopies copies = new PlaylistCopies(copyRepository, playlistRepository, providers);
    final PlaylistService playlists = new PlaylistService(playlistRepository, recommendationRepository, library,
            songs, artists, providers, copies, clock);
    final PlayService plays = new PlayService(playRepository, peopleRepository, playlistRepository,
            recommendationRepository, copyRepository, List.of(), clock);
    final CandidateService candidates = new CandidateService(library, plays, songs, artists, recommendations, List.of(lastFm),
            providers, clock);

    Person tidalUser(String name) {
        Person person = people.add(name, Optional.of(Provider.TIDAL));
        tidal.connectedPeople.add(person.id());
        return person;
    }

    Person withoutService(String name) {
        return people.add(name, Optional.empty());
    }

    static AlbumRef album(String artist, String title) {
        return AlbumRef.of(artist, title);
    }

    static AlbumRef album(String artist, String title, int year) {
        return new AlbumRef(artist, title, Optional.of(year), ProviderIds.NONE);
    }
}
