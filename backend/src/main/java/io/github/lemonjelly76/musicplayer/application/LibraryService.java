package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LibraryService {

    private static final System.Logger LOG = System.getLogger(LibraryService.class.getName());

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

    /**
     * Every album the person has rated, whatever the rating, by artist.
     */
    public List<LibraryAlbum> all(PersonId person, LibraryFilter filter) {
        return repository.findByPerson(person).stream().filter(filter::matches).sorted(BY_ARTIST).toList();
    }

    /**
     * The person's library for one activity (none for General): albums rated love or OK there, loved first.
     */
    public List<LibraryAlbum> libraryFor(PersonId person, Optional<CategoryId> activity, LibraryFilter filter) {
        return repository.findByPerson(person).stream()
                .filter(entry -> entry.keptFor(activity))
                .filter(filter::matches)
                .sorted(lovedFirst(activity))
                .toList();
    }

    private static Comparator<LibraryAlbum> lovedFirst(Optional<CategoryId> activity) {
        return Comparator.comparing((LibraryAlbum entry) ->
                entry.own(activity).map(found -> found.rating().value()).orElse(0)).reversed().thenComparing(BY_ARTIST);
    }

    /**
     * What the person turned down for the activity itself, by artist, so a dislike can be found and taken back.
     */
    public List<LibraryAlbum> dislikedFor(PersonId person, Optional<CategoryId> activity) {
        return repository.findByPerson(person).stream()
                .filter(entry -> entry.own(activity).map(found -> found.rating() == Rating.DISLIKE).orElse(false))
                .sorted(BY_ARTIST)
                .toList();
    }

    /**
     * What to leave out of anything made for the activity: turned down there, or in General with no rating there.
     */
    public List<LibraryAlbum> avoidedFor(PersonId person, Optional<CategoryId> activity) {
        return repository.findByPerson(person).stream().filter(entry -> entry.avoidedFor(activity)).toList();
    }

    /**
     * Albums the person keeps for some activity but has not rated for this one, to sort through quickly: loved in
     * General first, then OK in General, then those kept only for other activities.
     */
    public List<LibraryAlbum> toSortFor(PersonId person, Optional<CategoryId> activity) {
        return repository.findByPerson(person).stream()
                .filter(entry -> entry.own(activity).isEmpty())
                .filter(entry -> entry.ratings().stream().anyMatch(found -> found.rating().keeps()))
                .sorted(Comparator.comparing((LibraryAlbum entry) -> entry.ownOrGeneral(activity)
                        .map(found -> found.rating().value()).orElse(0)).reversed().thenComparing(BY_ARTIST))
                .toList();
    }

    public List<LibraryAlbum> entries(PersonId person) {
        return repository.findByPerson(person);
    }

    /**
     * The person's albums by key, for looking up their rating of any album.
     */
    public Map<AlbumKey, LibraryAlbum> byKey(PersonId person) {
        return repository.findByPerson(person).stream()
                .collect(Collectors.toMap(entry -> entry.album().key(), entry -> entry, (first, second) -> first));
    }

    public Set<AlbumKey> keys(PersonId person) {
        return repository.findByPerson(person).stream().map(entry -> entry.album().key()).collect(Collectors.toSet());
    }

    public Optional<LibraryAlbum> find(PersonId person, AlbumKey key) {
        return repository.findByPerson(person).stream().filter(entry -> entry.album().key().equals(key)).findFirst();
    }

    /**
     * Adds an album the person likes, rated for the activity (General when none), and saves it to their streaming
     * favourites when loved. An album already there keeps its notes and tags and takes the new rating.
     */
    public Added add(Person person, String reference, Optional<Category> activity, Rating rating, String notes,
            List<String> tags) {
        return add(person, resolveLeniently(person, reference), activity, rating, notes, tags);
    }

    public Added add(Person person, AlbumRef album, Optional<Category> activity, Rating rating, String notes,
            List<String> tags) {
        return saveRating(person, album, new ActivityRating(activity.map(Category::id), rating, "", clock.instant()),
                notes, tags);
    }

    /**
     * Rates an album for an activity (General when none), adding it when new. An empty rating takes the activity's
     * own rating away; an album left with no rating at all leaves the library.
     */
    public Optional<LibraryAlbum> rate(Person person, String reference, Optional<Category> activity,
            Optional<Rating> rating, String comment) {
        return rate(person, resolveLeniently(person, reference), activity, rating, comment);
    }

    public Optional<LibraryAlbum> rate(Person person, AlbumRef album, Optional<Category> activity,
            Optional<Rating> rating, String comment) {
        Optional<CategoryId> category = activity.map(Category::id);
        Optional<LibraryAlbum> existing = find(person.id(), album.key());
        if (rating.isEmpty()) {
            existing.ifPresent(entry -> keepOrRemove(entry.unrated(category)));
            return existing.map(entry -> entry.unrated(category)).filter(entry -> !entry.ratings().isEmpty());
        }
        ActivityRating given = new ActivityRating(category, rating.get(), comment, clock.instant());
        return Optional.of(saveRating(person, album, given, "", List.of()).entry());
    }

    /**
     * Re-rates an album already in the library, keeping its notes and tags, or adds it with them; a new loved album
     * also goes to the streaming favourites.
     */
    private Added saveRating(Person person, AlbumRef album, ActivityRating given, String notes, List<String> tags) {
        Optional<LibraryAlbum> existing = find(person.id(), album.key());
        if (existing.isPresent()) {
            LibraryAlbum rerated = existing.get().rated(given);
            repository.save(rerated);
            return new Added(rerated, true, "");
        }
        LibraryAlbum entry = LibraryAlbum.create(person.id(), album, given, notes, tags, LocalDate.now(clock));
        repository.save(entry);
        return new Added(entry, false, given.rating() == Rating.LOVE ? saveFavourite(person, album) : "");
    }

    public LibraryAlbum update(PersonId person, LibraryAlbumId id, Optional<String> notes, Optional<List<String>> tags) {
        LibraryAlbum entry = get(person, id);
        LibraryAlbum changed = entry
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
                repository.save(LibraryAlbum.create(person.id(), album,
                        ActivityRating.general(Rating.LOVE, clock.instant()), "", List.of("imported"), today));
                added++;
            } else {
                skipped++;
            }
        }
        return new Imported(service.name().prefix(), added, skipped);
    }

    /**
     * Finds, on the person's service, albums added before it was connected or while it was another service, so they
     * open straight in its app, and covers for albums that have none. At most the given number are looked up per
     * call, keeping the call quick; the rest follow next time. Returns how many were filled in.
     */
    public int fillServiceIds(Person person, int maxLookups) {
        Optional<MusicProvider> service = providers.forPerson(person).filter(found -> found.connected(person.id()));
        if (service.isEmpty()) {
            return 0;
        }
        MusicProvider provider = service.get();
        List<LibraryAlbum> incomplete = repository.findByPerson(person.id()).stream()
                .filter(entry -> entry.album().ids().get(provider.name()).isEmpty() || entry.album().cover().isEmpty())
                .limit(maxLookups)
                .toList();
        int filled = 0;
        try {
            for (LibraryAlbum entry : incomplete) {
                Optional<AlbumRef> found = AlbumResolver.onService(provider, entry.album());
                if (found.isPresent()) {
                    repository.save(entry.withAlbum(entry.album().enrichedWith(found.get())));
                    filled++;
                }
            }
        } catch (MusicProviderUnavailableException e) {
            LOG.log(System.Logger.Level.WARNING, "Stopped filling in " + provider.name().prefix() + " albums", e);
        }
        return filled;
    }

    private static final Comparator<LibraryAlbum> BY_ARTIST = Comparator
            .comparing((LibraryAlbum entry) -> entry.album().artist().toLowerCase(Locale.ROOT))
            .thenComparing(entry -> entry.album().year().orElse(0));

    private void keepOrRemove(LibraryAlbum entry) {
        if (entry.ratings().isEmpty()) {
            repository.delete(entry.id());
        } else {
            repository.save(entry);
        }
    }

    private AlbumRef resolveLeniently(Person person, String reference) {
        return resolver.resolveLeniently(reference, providers.forPerson(person))
                .orElseThrow(() -> new IllegalArgumentException("'" + reference + "' is not an album reference; "
                        + "use an id from search_catalogue such as tidal:251380836, or 'Artist — Title'"));
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
