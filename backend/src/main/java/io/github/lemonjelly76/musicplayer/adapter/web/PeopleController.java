package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.web.Requests.CategoryOrderRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Requests.CategoryRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Requests.ServiceRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Requests.TrackingRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.CategoryResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.CategoryTracking;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.PersonResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.ServiceResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.TrackingResponse;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlayService;
import io.github.lemonjelly76.musicplayer.application.PlaylistService;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayTracking;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api")
class PeopleController {

    private final PersonService people;
    private final CategoryService categories;
    private final PlayService plays;
    private final MusicProviders providers;
    private final PlaylistService playlists;

    PeopleController(PersonService people, CategoryService categories, PlayService plays, MusicProviders providers,
            PlaylistService playlists) {
        this.people = people;
        this.categories = categories;
        this.plays = plays;
        this.providers = providers;
        this.playlists = playlists;
    }

    @GetMapping("/people")
    List<PersonResponse> people() {
        return people.all().stream()
                .map(this::response)
                .toList();
    }

    /**
     * The services the server is registered with; a person can only connect one of these.
     */
    @GetMapping("/services")
    List<ServiceResponse> services() {
        return Arrays.stream(Provider.values())
                .map(service -> new ServiceResponse(service.prefix(), providers.find(service).isPresent()))
                .toList();
    }

    @PutMapping("/people/{id}/service")
    PersonResponse setService(@PathVariable String id, @RequestBody ServiceRequest request) {
        Person person = people.get(PersonId.of(id));
        Optional<Provider> service = Optional.ofNullable(request.service()).filter(text -> !text.isBlank())
                .map(Provider::parse);
        Person changed = people.update(person.withProvider(service));
        return response(changed);
    }

    private PersonResponse response(Person person) {
        return PersonResponse.from(person, providers.forPerson(person)
                .map(provider -> provider.connected(person.id())).orElse(false), providers.alsoConnected(person));
    }

    @GetMapping("/categories")
    List<CategoryResponse> categories() {
        return categories.all().stream().map(CategoryResponse::from).toList();
    }

    /**
     * Puts the activities in the household's chosen order.
     */
    @PutMapping("/categories/order")
    List<CategoryResponse> reorderCategories(@RequestBody CategoryOrderRequest request) {
        categories.reorder(Objects.requireNonNullElse(request.ids(), List.of()));
        return categories();
    }

    /**
     * Renames an activity for the whole household, and its playlists with it.
     */
    @PutMapping("/categories/{id}")
    CategoryResponse renameCategory(@PathVariable String id, @RequestBody CategoryRequest request) {
        Category renamed = categories.rename(id, request.name());
        playlists.renameFor(renamed);
        return CategoryResponse.from(renamed);
    }

    /**
     * Removes an activity for the whole household, with every rating, album list and playlist for it.
     */
    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeCategory(@PathVariable String id) {
        categories.remove(id);
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
