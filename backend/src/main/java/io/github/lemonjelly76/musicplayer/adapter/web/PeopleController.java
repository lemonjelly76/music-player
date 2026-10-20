package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.web.Requests.TrackingRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.CategoryResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.CategoryTracking;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.PersonResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.TrackingResponse;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlayService;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayTracking;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
class PeopleController {

    private final PersonService people;
    private final CategoryService categories;
    private final PlayService plays;
    private final MusicProviders providers;

    PeopleController(PersonService people, CategoryService categories, PlayService plays, MusicProviders providers) {
        this.people = people;
        this.categories = categories;
        this.plays = plays;
        this.providers = providers;
    }

    @GetMapping("/people")
    List<PersonResponse> people() {
        return people.all().stream()
                .map(person -> PersonResponse.from(person, providers.forPerson(person)
                        .map(provider -> provider.connected(person.id())).orElse(false)))
                .toList();
    }

    @GetMapping("/categories")
    List<CategoryResponse> categories() {
        return categories.all().stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/people/{id}/play-tracking")
    TrackingResponse tracking(@PathVariable String id) {
        return tracking(people.get(PersonId.of(id)));
    }

    @PutMapping("/people/{id}/play-tracking")
    TrackingResponse setTracking(@PathVariable String id, @RequestBody TrackingRequest request) {
        Person person = people.get(PersonId.of(id));
        Optional<Category> category = Optional.ofNullable(request.categoryId()).map(categories::find);
        plays.setTracking(person, category, Optional.ofNullable(request.enabled()));
        return tracking(people.get(person.id()));
    }

    private TrackingResponse tracking(Person person) {
        PlayTracking tracking = plays.tracking(person);
        List<CategoryTracking> perCategory = categories.all().stream()
                .map(category -> new CategoryTracking(category.id().toString(), category.name(),
                        tracking.enabledFor(Optional.of(category.id())), tracking.overrides().containsKey(category.id())))
                .toList();
        return new TrackingResponse(tracking.personEnabled(), perCategory);
    }
}
