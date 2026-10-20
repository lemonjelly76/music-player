package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbumId;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryFilter;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.LibraryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LibraryService {

    private static final Comparator<LibraryAlbum> BEST_RATED_THEN_ARTIST = Comparator
            .comparing((LibraryAlbum entry) -> entry.rating().map(Rating::value).orElse(0)).reversed()
            .thenComparing(entry -> entry.album().artist().toLowerCase())
            .thenComparing(entry -> entry.album().year().orElse(0));

    private final LibraryRepository repository;
    private final AlbumResolver resolver;
    private final MusicProviders providers;
    private final Clock clock;

    public LibraryService(LibraryRepository repository, AlbumResolver resolver, MusicProviders providers, Clock clock) {
        this.repository = repository;
        this.resolver = resolver;
        this.providers = providers;
        this.clock = clock;
    }

    public List<LibraryAlbum> list(PersonId person, LibraryFilter filter) {
        return repository.findByPerson(person).stream().filter(filter::matches).sorted(BEST_RATED_THEN_ARTIST).toList();
    }

    public Set<AlbumKey> keys(PersonId person) {
        return repository.findByPerson(person).stream().map(entry -> entry.album().key()).collect(Collectors.toSet());
    }

    public Optional<LibraryAlbum> find(PersonId person, AlbumKey key) {
        return repository.findByPerson(person).stream().filter(entry -> entry.album().key().equals(key)).findFirst();
    }

    /**
     * Adds the album and saves it to the person's streaming favourites too. An album already in the library is
     * left as it is and reported as such.
     */
    public Added add(Person person, String reference, Optional<Rating> rating, String notes, List<String> tags) {
        Optional<MusicProvider> service = providers.forPerson(person);
        AlbumRef album = resolver.resolveLeniently(reference, service)
                .orElseThrow(() -> new IllegalArgumentException("'" + reference + "' is not an album reference; "
                        + "use an id from search_catalogue such as tidal:251380836, or 'Artist — Title'"));
        return add(person, album, rating, notes, tags);
    }

    public Added add(Person person, AlbumRef album, Optional<Rating> rating, String notes, List<String> tags) {
        Optional<LibraryAlbum> existing = find(person.id(), album.key());
        if (existing.isPresent()) {
            return new Added(existing.get(), true, "");
        }
        LibraryAlbum entry = LibraryAlbum.create(person.id(), album, rating, notes, tags, LocalDate.now(clock));
        repository.save(entry);
        return new Added(entry, false, saveFavourite(person, album));
    }

    public LibraryAlbum update(PersonId person, LibraryAlbumId id, Optional<Rating> rating, Optional<String> notes,
            Optional<List<String>> tags) {
        LibraryAlbum entry = get(person, id);
        LibraryAlbum changed = entry
                .withRating(rating.or(entry::rating))
                .withNotes(notes.orElse(entry.notes()))
                .withTags(tags.orElse(List.copyOf(entry.tags())));
        repository.save(changed);
        return changed;
    }

    public void remove(PersonId person, LibraryAlbumId id) {
        repository.delete(get(person, id).id());
    }

    /**
     * Seeds the library from the albums the person already saved on their streaming service.
     */
    public Imported importFavourites(Person person) {
        MusicProvider service = providers.forPerson(person).orElseThrow(() -> new MusicProviderUnavailableException(
                person.name() + " has no streaming service linked; set one with set_person_service"));
        Set<AlbumKey> known = keys(person.id());
        LocalDate today = LocalDate.now(clock);
        int added = 0;
        int skipped = 0;
        for (AlbumRef album : service.favouriteAlbums(person.id())) {
            if (known.add(album.key())) {
                repository.save(LibraryAlbum.create(person.id(), album, Optional.empty(), "",
                        List.of("imported"), today));
                added++;
            } else {
                skipped++;
            }
        }
        return new Imported(service.name().prefix(), added, skipped);
    }

    private LibraryAlbum get(PersonId person, LibraryAlbumId id) {
        return repository.findById(id)
                .filter(entry -> entry.person().equals(person))
                .orElseThrow(() -> new NotFoundException("No album " + id + " in this person's library"));
    }

    private String saveFavourite(Person person, AlbumRef album) {
        Optional<MusicProvider> service = providers.forPerson(person);
        if (service.isEmpty()) {
            return "";
        }
        Optional<String> id = album.ids().get(service.get().name());
        if (id.isEmpty()) {
            return "not saved to " + service.get().name().prefix() + " favourites: it is not on the service";
        }
        try {
            service.get().addFavourite(person.id(), id.get());
            return "";
        } catch (MusicProviderUnavailableException e) {
            return "not saved to " + service.get().name().prefix() + " favourites: " + e.getMessage();
        }
    }

    /**
     * @param favouriteProblem why the album did not reach the streaming favourites, empty when it did or no
     *                         service is linked
     */
    public record Added(LibraryAlbum entry, boolean alreadyInLibrary, String favouriteProblem) {
    }

    public record Imported(String provider, int added, int alreadyInLibrary) {
    }
}
