package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.web.Requests.FeedbackRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Requests.LibraryRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.CandidateResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.LibraryAlbumResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.ListResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.PickResponse;
import io.github.lemonjelly76.musicplayer.application.CandidateService;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.LibraryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.RecommendationService;
import io.github.lemonjelly76.musicplayer.application.RecommendationService.Judged;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryFilter;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationId;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Verdict;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The app's picks, library and "More like this". The app never talks to the model; it reads what the model wrote
 * (ADR-004).
 */
@RestController
@RequestMapping("/api")
class MusicController {

    static final int MAX_LIMIT = 50;

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
     * The person's lists, newest first; a category id narrows them to that category, "none" to the general list.
     */
    @GetMapping("/people/{id}/lists")
    List<ListResponse> lists(@PathVariable String id, @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "5") int limit) {
        Person person = people.get(PersonId.of(id));
        Function<AlbumRef, Optional<URI>> links = album -> providers.link(person, album);
        List<RecommendationList> lists;
        if (category == null || category.isBlank()) {
            lists = recommendations.history(person, limit(limit));
        } else if (category.equals("none")) {
            lists = recommendations.history(person, MAX_LIMIT).stream()
                    .filter(list -> list.category().isEmpty()).limit(limit(limit)).toList();
        } else {
            lists = recommendations.lists(person, Optional.of(categories.find(category)), limit(limit));
        }
        return lists.stream()
                .map(list -> ListResponse.from(list, list.playlist().flatMap(providers::link), links))
                .toList();
    }

    @PostMapping("/picks/{id}/feedback")
    PickResponse feedback(@PathVariable String id, @RequestBody FeedbackRequest request) {
        Judged judged = recommendations.feedback(RecommendationId.of(id),
                Verdict.parse(Objects.requireNonNullElse(request.verdict(), "")),
                Objects.requireNonNullElse(request.comment(), ""));
        Person person = people.get(judged.list().person());
        return PickResponse.from(judged.pick(), album -> providers.link(person, album));
    }

    @GetMapping("/people/{id}/library")
    List<LibraryAlbumResponse> library(@PathVariable String id) {
        Person person = people.get(PersonId.of(id));
        return library.list(person.id(), LibraryFilter.ALL).stream()
                .map(entry -> LibraryAlbumResponse.from(entry, album -> providers.link(person, album)))
                .toList();
    }

    @PostMapping("/people/{id}/library")
    @ResponseStatus(HttpStatus.CREATED)
    LibraryAlbumResponse addToLibrary(@PathVariable String id, @RequestBody LibraryRequest request) {
        Person person = people.get(PersonId.of(id));
        LibraryService.Added added = library.add(person, Objects.requireNonNullElse(request.albumRef(), ""),
                Optional.ofNullable(request.rating()).map(Rating::new),
                Objects.requireNonNullElse(request.notes(), ""),
                Objects.requireNonNullElse(request.tags(), List.of()));
        return LibraryAlbumResponse.from(added.entry(), album -> providers.link(person, album));
    }

    /**
     * Instant suggestions from one album: the server's ranked pool, no model call (ADR-007).
     */
    @GetMapping("/people/{id}/more-like-this")
    List<CandidateResponse> moreLikeThis(@PathVariable String id, @RequestParam String artist,
            @RequestParam String title, @RequestParam(defaultValue = "10") int limit) {
        Person person = people.get(PersonId.of(id));
        return candidates.moreLikeThis(person, AlbumRef.of(artist, title), limit(limit)).stream()
                .map(candidate -> CandidateResponse.from(candidate, album -> providers.link(person, album)))
                .toList();
    }

    private static int limit(int requested) {
        if (requested < 1 || requested > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return requested;
    }
}
