package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtist;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.ArtistRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Artists rated per activity, as albums and songs are: a loved artist leads to their albums and artists like them
 * for that activity, and one turned down there keeps all their albums and songs out of it.
 */
public class ArtistService {

    private final ArtistRepository repository;
    private final Clock clock;

    public ArtistService(ArtistRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Rates an artist for an activity (Mainstream when none), adding them when new. An empty rating takes the
     * activity's own rating away, and an artist left with none leaves the library.
     */
    public Optional<LibraryArtist> rate(Person person, String name, Optional<Category> activity,
            Optional<Rating> rating, String comment) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Say which artist");
        }
        Optional<CategoryId> category = activity.map(Category::id);
        Optional<LibraryArtist> existing = repository.findByPerson(person.id()).stream()
                .filter(artist -> artist.isCalled(name))
                .findFirst();
        if (rating.isEmpty()) {
            Optional<LibraryArtist> left = existing.map(artist -> artist.unrated(category));
            left.ifPresent(artist -> {
                if (artist.ratings().isEmpty()) {
                    repository.delete(artist.id());
                } else {
                    repository.save(artist);
                }
            });
            return left.filter(artist -> !artist.ratings().isEmpty());
        }
        ActivityRating given = new ActivityRating(category, rating.get(), comment, clock.instant());
        LibraryArtist artist = existing.map(found -> found.rated(given))
                .orElseGet(() -> LibraryArtist.create(person.id(), name, given, LocalDate.now(clock)));
        repository.save(artist);
        return Optional.of(artist);
    }

    /**
     * The artists rated for the activity itself: loved first, then OK, then those turned down, each by name.
     */
    public List<LibraryArtist> artistsFor(PersonId person, Optional<CategoryId> activity) {
        return repository.findByPerson(person).stream()
                .filter(artist -> artist.own(activity).isPresent())
                .sorted(Comparator.comparing((LibraryArtist artist) -> artist.own(activity).orElseThrow().rating().value())
                        .reversed()
                        .thenComparing(artist -> artist.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    public List<LibraryArtist> lovedFor(PersonId person, Optional<CategoryId> activity) {
        return artistsFor(person, activity).stream()
                .filter(artist -> artist.own(activity).orElseThrow().rating() == Rating.LOVE)
                .toList();
    }

    /**
     * The artists turned down for the activity, by {@link LibraryArtist#key(String)}, with the name as they rated it.
     */
    public Map<String, String> turnedDown(PersonId person, Optional<CategoryId> activity) {
        return repository.findByPerson(person).stream()
                .filter(artist -> artist.avoidedFor(activity))
                .collect(Collectors.toMap(artist -> LibraryArtist.key(artist.name()), LibraryArtist::name,
                        (first, second) -> first));
    }
}
