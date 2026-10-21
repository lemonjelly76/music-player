package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.web.Requests.RatingRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.CandidateResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.FoundResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.LibraryAlbumResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.ListResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.RatingResponse;
import io.github.lemonjelly76.musicplayer.application.CandidateService;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.LibraryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.RecommendationService;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryFilter;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The app's picks, library, ratings and "More like this". The app never talks to the model; it reads what the model
 * wrote (ADR-004). Activities are category ids; "none", or leaving one out, is General.
 */
@RestController
@RequestMapping("/api")
class MusicController {

    static final int MAX_LIMIT = 50;
    static final String GENERAL = ActivityParameter.GENERAL;
    /**
     * Albums looked up on the person's service each time a screen opens, so ids and covers fill in a few at a time.
     */
    static final int LOOKUPS_PER_VISIT = 10;

    private final PersonService people;
    private final CategoryService categories;
    private final LibraryService library;
    private final RecommendationService recommendations;
    private final CandidateService candidates;
    private final MusicProviders providers;

    MusicController(PersonService people, CategoryService categories, LibraryService library,
            RecommendationService recommendations, CandidateService candidates, MusicProviders providers) {
        this.people = people;
        this.categories = categories;
        this.library = library;
        this.recommendations = recommendations;
        this.candidates = candidates;
        this.providers = providers;
    }

    /**
     * The person's latest lists for the activity, with their rating of each pick there; no category gives every
     * list.
     */
    @GetMapping("/people/{id}/lists")
    List<ListResponse> lists(@PathVariable String id, @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "5") int limit) {
        Person person = people.get(PersonId.of(id));
        List<RecommendationList> lists;
        if (category == null || category.isBlank()) {
            lists = recommendations.history(person, limit(limit));
        } else if (category.equals(GENERAL)) {
            lists = recommendations.history(person, MAX_LIMIT).stream()
                    .filter(list -> list.category().isEmpty()).limit(limit(limit)).toList();
        } else {
            lists = recommendations.lists(person, Optional.of(categories.find(category)), limit(limit));
        }
        Map<AlbumKey, LibraryAlbum> rated = library.byKey(person.id());
        Set<Optional<CategoryId>> newestSeen = new HashSet<>();
        return lists.stream()
                .map(list -> recommendations.withCovers(person, list, LOOKUPS_PER_VISIT))
                .map(list -> ListResponse.from(list, livePlaylist(list, newestSeen), rated, links(person)))
                .toList();
    }

    /**
     * Each new list replaces its activity's playlist, so only the newest list for an activity, the first one seen
     * here, still has one to open.
     */
    private Optional<URI> livePlaylist(RecommendationList list, Set<Optional<CategoryId>> newestSeen) {
        boolean newest = newestSeen.add(list.category());
        return newest ? list.playlist().flatMap(providers::link) : Optional.empty();
    }

    /**
     * The person's library for the activity: albums they love or rate OK there (or in General), loved first.
     */
    @GetMapping("/people/{id}/library")
    List<LibraryAlbumResponse> library(@PathVariable String id, @RequestParam(required = false) String category) {
        Person person = people.get(PersonId.of(id));
        library.fillServiceIds(person, LOOKUPS_PER_VISIT);
        Optional<Category> activity = activity(category);
        return library.libraryFor(person.id(), activity.map(Category::id), LibraryFilter.ALL).stream()
                .map(entry -> LibraryAlbumResponse.from(entry, activity.map(Category::id), links(person)))
                .toList();
    }

    /**
     * Albums the person has turned down for the activity, so a dislike can be found again and taken back.
     */
    @GetMapping("/people/{id}/library/disliked")
    List<LibraryAlbumResponse> disliked(@PathVariable String id, @RequestParam(required = false) String category) {
        Person person = people.get(PersonId.of(id));
        Optional<CategoryId> activity = activity(category).map(Category::id);
        return library.dislikedFor(person.id(), activity).stream()
                .map(entry -> LibraryAlbumResponse.from(entry, activity, links(person)))
                .toList();
    }

    /**
     * Albums kept for other activities, or in General, that the person has not yet rated for this one.
     */
    @GetMapping("/people/{id}/library/to-sort")
    List<LibraryAlbumResponse> toSort(@PathVariable String id, @RequestParam(required = false) String category) {
        Person person = people.get(PersonId.of(id));
        Optional<CategoryId> activity = activity(category).map(Category::id);
        return library.toSortFor(person.id(), activity).stream()
                .map(entry -> LibraryAlbumResponse.from(entry, activity, links(person)))
                .toList();
    }

    /**
     * Albums from the person's streaming catalogue, with their rating of each for the activity, so ones they already
     * know can be rated straight away.
     */
    @GetMapping("/people/{id}/search")
    List<FoundResponse> search(@PathVariable String id, @RequestParam String q,
            @RequestParam(required = false) String category, @RequestParam(defaultValue = "20") int limit) {
        if (q.isBlank()) {
            throw new IllegalArgumentException("Type something to search for");
        }
        Person person = people.get(PersonId.of(id));
        Optional<CategoryId> activity = activity(category).map(Category::id);
        Map<AlbumKey, LibraryAlbum> rated = library.byKey(person.id());
        return providers.search(person, q.strip(), limit(limit)).stream()
                .map(album -> FoundResponse.from(album, Optional.ofNullable(rated.get(album.key())), activity,
                        links(person)))
                .toList();
    }

    /**
     * Rates any album the app shows (a pick, a library album, a suggestion) for an activity.
     */
    @PostMapping("/people/{id}/ratings")
    RatingResponse rate(@PathVariable String id, @RequestBody RatingRequest request) {
        Person person = people.get(PersonId.of(id));
        String albumRef = Objects.requireNonNullElse(request.albumRef(), "");
        Optional<LibraryAlbum> rated = library.rate(person, albumRef, activity(request.categoryId()),
                Optional.ofNullable(request.rating()).map(Rating::of),
                Objects.requireNonNullElse(request.comment(), ""));
        Integer rating = rated.flatMap(entry -> entry.own(activity(request.categoryId()).map(Category::id)))
                .map(found -> found.rating().value())
                .orElse(null);
        return new RatingResponse(albumRef, rating);
    }

    /**
     * Instant suggestions from one album: the server's ranked pool, no model call (ADR-007).
     */
    @GetMapping("/people/{id}/more-like-this")
    List<CandidateResponse> moreLikeThis(@PathVariable String id, @RequestParam String artist,
            @RequestParam String title, @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int limit) {
        Person person = people.get(PersonId.of(id));
        return candidates.moreLikeThis(person, AlbumRef.of(artist, title), activity(category), limit(limit)).stream()
                .map(candidate -> CandidateResponse.from(candidate, links(person)))
                .toList();
    }

    private Optional<Category> activity(String category) {
        return ActivityParameter.resolve(categories, category);
    }

    private Function<AlbumRef, Optional<URI>> links(Person person) {
        return album -> providers.link(person, album);
    }

    private static int limit(int requested) {
        if (requested < 1 || requested > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return requested;
    }
}
