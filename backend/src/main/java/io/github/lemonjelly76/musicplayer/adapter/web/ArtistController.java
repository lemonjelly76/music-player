package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.web.Requests.ArtistRatingRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.ArtistRatingResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.RatedArtistResponse;
import io.github.lemonjelly76.musicplayer.application.ArtistService;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Artists the person has rated for an activity, and rating any artist the app shows.
 */
@RestController
@RequestMapping("/api")
class ArtistController {

    private final PersonService people;
    private final CategoryService categories;
    private final ArtistService artists;

    ArtistController(PersonService people, CategoryService categories, ArtistService artists) {
        this.people = people;
        this.categories = categories;
        this.artists = artists;
    }

    /**
     * The artists rated for the activity: loved first, then OK, then those turned down.
     */
    @GetMapping("/people/{id}/artists")
    List<RatedArtistResponse> artists(@PathVariable String id, @RequestParam(required = false) String category) {
        Person person = people.get(PersonId.of(id));
        Optional<CategoryId> activity = ActivityParameter.resolve(categories, category).map(Category::id);
        return artists.artistsFor(person.id(), activity).stream()
                .map(artist -> RatedArtistResponse.from(artist, activity))
                .toList();
    }

    @PostMapping("/people/{id}/artist-ratings")
    ArtistRatingResponse rate(@PathVariable String id, @RequestBody ArtistRatingRequest request) {
        Person person = people.get(PersonId.of(id));
        Optional<Category> activity = ActivityParameter.resolve(categories, request.categoryId());
        Optional<CategoryId> category = activity.map(Category::id);
        return new ArtistRatingResponse(request.artist(), artists.rate(person, request.artist(), activity,
                        Optional.ofNullable(request.rating()).map(Rating::of),
                        Objects.requireNonNullElse(request.comment(), ""))
                .flatMap(artist -> artist.own(category))
                .map(found -> found.rating().value())
                .orElse(null));
    }
}
