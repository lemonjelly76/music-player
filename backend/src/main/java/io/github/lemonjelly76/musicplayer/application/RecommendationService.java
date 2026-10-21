package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.AlbumResolver.Resolution;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.port.RecommendationRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Step 6 of the pipeline: checks every pick the model makes against the catalogue and what the person has rated, and
 * keeps the list of albums to try. Albums are opened one at a time, so a list writes no playlist; song playlists are
 * {@link PlaylistService}'s. What they think of a pick is a rating in their library (ADR-015).
 */
public class RecommendationService {

    private final RecommendationRepository repository;
    private final AlbumResolver resolver;
    private final LibraryService library;
    private final MusicProviders providers;
    private final Clock clock;

    public RecommendationService(RecommendationRepository repository, AlbumResolver resolver, LibraryService library,
            MusicProviders providers, Clock clock) {
        this.repository = repository;
        this.resolver = resolver;
        this.library = library;
        this.providers = providers;
        this.clock = clock;
    }

    public Saved save(Person person, Optional<Category> category, List<PickRequest> requests) {
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("A list needs at least one pick");
        }
        Optional<MusicProvider> service = providers.forPerson(person);
        Optional<CategoryId> activity = category.map(Category::id);
        Set<AlbumKey> known = library.entries(person.id()).stream()
                .filter(entry -> entry.knownFor(activity))
                .map(entry -> entry.album().key())
                .collect(Collectors.toSet());
        Set<AlbumKey> inList = new HashSet<>();
        List<Recommendation> picks = new ArrayList<>();
        List<Dropped> dropped = new ArrayList<>();
        for (PickRequest request : requests) {
            Optional<String> repeat = resolver.named(request.albumRef())
                    .flatMap(named -> whyNotOffered(named.key(), known, inList));
            if (repeat.isPresent()) {
                dropped.add(new Dropped(request.albumRef(), repeat.get()));
                continue;
            }
            Resolution resolution = resolver.resolve(request.albumRef(), service);
            if (resolution.album().isEmpty()) {
                dropped.add(new Dropped(request.albumRef(), resolution.problem()));
                continue;
            }
            AlbumRef album = resolution.album().get();
            Optional<String> why = whyNotOffered(album.key(), known, inList);
            if (why.isPresent()) {
                dropped.add(new Dropped(request.albumRef(), why.get()));
            } else {
                inList.add(album.key());
                picks.add(Recommendation.create(album, request.reason(), request.source()));
            }
        }
        if (picks.isEmpty()) {
            throw new IllegalArgumentException("No pick could be kept: " + dropped);
        }
        RecommendationList list = RecommendationList.create(person.id(), category, picks, clock.instant());
        repository.save(list);
        return new Saved(list, dropped);
    }

    /**
     * The list with covers looked up for picks that have none, at most the given number per call; the list is saved
     * with whatever was found.
     */
    public RecommendationList withCovers(Person person, RecommendationList list, int maxLookups) {
        Optional<MusicProvider> service = providers.forPerson(person);
        if (service.isEmpty() || list.picks().stream().allMatch(pick -> pick.album().cover().isPresent())) {
            return list;
        }
        List<Recommendation> picks = new ArrayList<>();
        int lookups = 0;
        try {
            for (Recommendation pick : list.picks()) {
                if (pick.album().cover().isEmpty() && lookups++ < maxLookups) {
                    Optional<AlbumRef> found = AlbumResolver.onService(service.get(), pick.album());
                    picks.add(found.map(album -> new Recommendation(pick.id(), pick.album().enrichedWith(album),
                            pick.reason(), pick.source())).orElse(pick));
                } else {
                    picks.add(pick);
                }
            }
        } catch (MusicProviderUnavailableException e) {
            return list;
        }
        RecommendationList covered = new RecommendationList(list.id(), list.person(), list.category(), list.name(),
                list.createdAt(), list.playlist(), picks);
        repository.save(covered);
        return covered;
    }

    public List<RecommendationList> lists(Person person, Optional<Category> category, int limit) {
        return repository.findByPerson(person.id(), category.map(Category::id), limit);
    }

    /**
     * Every list of the person's, whatever its category, newest first.
     */
    public List<RecommendationList> history(Person person, int limit) {
        return repository.findByPerson(person.id(), Optional.empty(), limit);
    }

    /**
     * Checked by name before any lookup too, so a repeat costs no catalogue call and gets the right reason.
     */
    private static Optional<String> whyNotOffered(AlbumKey key, Set<AlbumKey> known, Set<AlbumKey> inList) {
        if (known.contains(key)) {
            return Optional.of("already rated");
        }
        if (inList.contains(key)) {
            return Optional.of("already in this list");
        }
        return Optional.empty();
    }

    public record PickRequest(String albumRef, String reason, PickSource source) {
    }

    public record Dropped(String albumRef, String why) {

        @Override
        public String toString() {
            return albumRef + " (" + why + ")";
        }
    }

    public record Saved(RecommendationList list, List<Dropped> dropped) {
    }
}
