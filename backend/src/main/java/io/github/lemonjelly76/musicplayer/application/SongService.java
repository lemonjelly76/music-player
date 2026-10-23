package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.port.SongRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Songs a person has rated, per activity as albums are (ADR-015): from playlists, search, or what they tell Claude.
 * Ratings steer playlists; a song turned down for an activity is left out of its playlists.
 */
public class SongService {

    private final SongRepository repository;
    private final MusicProviders providers;
    private final Clock clock;

    public SongService(SongRepository repository, MusicProviders providers, Clock clock) {
        this.repository = repository;
        this.providers = providers;
        this.clock = clock;
    }

    /**
     * Rates a song for an activity (Mainstream when none), adding it when new; the same song found on another service
     * or in another edition is the same entry. An empty rating takes the activity's own rating away, and a song left
     * with none leaves the library.
     */
    public Optional<LibrarySong> rate(Person person, Track track, Optional<Category> activity, Optional<Rating> rating) {
        Optional<CategoryId> category = activity.map(Category::id);
        Optional<LibrarySong> existing = find(person.id(), track);
        if (rating.isEmpty()) {
            Optional<LibrarySong> left = existing.map(song -> song.unrated(category));
            left.ifPresent(this::keepOrRemove);
            return left.filter(song -> !song.ratings().isEmpty());
        }
        ActivityRating given = new ActivityRating(category, rating.get(), "", clock.instant());
        LibrarySong song = existing.map(found -> found.withTrack(track).rated(given))
                .orElseGet(() -> LibrarySong.create(person.id(), track, given, LocalDate.now(clock)));
        repository.save(song);
        return Optional.of(song);
    }

    /**
     * Rates a song the person names, finding it on their service first unless it is already in their library.
     */
    public Optional<LibrarySong> rate(Person person, String artist, String title, Optional<Category> activity,
            Optional<Rating> rating) {
        String label = artist.strip() + " — " + title.strip();
        Track named = new Track(providers.forPerson(person).map(MusicProvider::name)
                .orElseThrow(() -> noService(person)), "", artist, title, "", Optional.empty());
        Track track = find(person.id(), named).map(LibrarySong::track)
                .or(() -> catalogue(person).findTrack(artist, title))
                .orElseThrow(() -> new NotFoundException(label + " is not on " + named.provider().prefix()));
        return rate(person, track, activity, rating);
    }

    /**
     * The person's songs rated for the activity itself: loved first, then OK, then those turned down, each by artist.
     */
    public List<LibrarySong> songsFor(PersonId person, Optional<CategoryId> activity) {
        Comparator<LibrarySong> byRating = Comparator.comparing(
                song -> song.own(activity).map(found -> found.rating().value()).orElse(0), Comparator.reverseOrder());
        return repository.findByPerson(person).stream()
                .filter(song -> song.own(activity).isPresent())
                .sorted(byRating.thenComparing(song -> song.track().artist().toLowerCase(Locale.ROOT))
                        .thenComparing(song -> song.track().title().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * The person's rated songs by {@link Track#songKey()}, to show each song's rating wherever it appears.
     */
    public Map<String, LibrarySong> bySong(PersonId person) {
        return repository.findByPerson(person).stream()
                .collect(Collectors.toMap(song -> song.track().songKey(), song -> song, (first, second) -> first));
    }

    /**
     * Songs to leave out of the activity's playlists, by song key: those turned down there.
     */
    public Set<String> turnedDown(PersonId person, Optional<CategoryId> activity) {
        return repository.findByPerson(person).stream()
                .filter(song -> song.avoidedFor(activity))
                .map(song -> song.track().songKey())
                .collect(Collectors.toSet());
    }

    public List<Track> search(Person person, String query, int limit) {
        return catalogue(person).searchTracks(query, limit);
    }

    private Optional<LibrarySong> find(PersonId person, Track track) {
        return repository.findByPerson(person).stream()
                .filter(song -> song.track().songKey().equals(track.songKey())
                        || song.track().provider() == track.provider() && song.track().id().equals(track.id()))
                .findFirst();
    }

    private void keepOrRemove(LibrarySong song) {
        if (song.ratings().isEmpty()) {
            repository.delete(song.id());
        } else {
            repository.save(song);
        }
    }

    private MusicProvider catalogue(Person person) {
        return providers.forPerson(person).orElseThrow(() -> noService(person));
    }

    private static MusicProviderUnavailableException noService(Person person) {
        return new MusicProviderUnavailableException(person.name() + " has no streaming service set up to find songs on");
    }
}
