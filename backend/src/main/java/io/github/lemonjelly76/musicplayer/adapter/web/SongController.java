package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.web.Requests.SongRatingRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.SongRatingResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.TrackResponse;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.SongService;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Songs the person has rated, songs to find in their catalogue, and rating any song the app shows.
 */
@RestController
@RequestMapping("/api")
class SongController {

    static final int MAX_LIMIT = 50;

    private final PersonService people;
    private final CategoryService categories;
    private final SongService songs;
    private final MusicProviders providers;

    SongController(PersonService people, CategoryService categories, SongService songs, MusicProviders providers) {
        this.people = people;
        this.categories = categories;
        this.songs = songs;
        this.providers = providers;
    }

    /**
     * The person's songs with a rating that applies to the activity, loved first; the app narrows them further.
     */
    @GetMapping("/people/{id}/songs")
    List<TrackResponse> songs(@PathVariable String id, @RequestParam(required = false) String category) {
        Person person = people.get(PersonId.of(id));
        Optional<CategoryId> activity = ActivityParameter.resolve(categories, category).map(Category::id);
        return songs.songsFor(person.id(), activity).stream()
                .map(song -> TrackResponse.from(song.track(), Optional.of(song), activity,
                        Links.forPerson(person, providers)))
                .toList();
    }

    @GetMapping("/people/{id}/songs/search")
    List<TrackResponse> search(@PathVariable String id, @RequestParam String q,
            @RequestParam(required = false) String category, @RequestParam(defaultValue = "20") int limit) {
        if (q.isBlank()) {
            throw new IllegalArgumentException("Type something to search for");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        Person person = people.get(PersonId.of(id));
        Optional<CategoryId> activity = ActivityParameter.resolve(categories, category).map(Category::id);
        Map<String, LibrarySong> rated = songs.bySong(person.id());
        return songs.search(person, q.strip(), limit).stream()
                .map(track -> TrackResponse.from(track, Optional.ofNullable(rated.get(track.songKey())), activity,
                        Links.forPerson(person, providers)))
                .toList();
    }

    @PostMapping("/people/{id}/song-ratings")
    SongRatingResponse rate(@PathVariable String id, @RequestBody SongRatingRequest request) {
        Track track = request.track();
        Person person = people.get(PersonId.of(id));
        Optional<Category> activity = ActivityParameter.resolve(categories, request.categoryId());
        Optional<LibrarySong> rated = songs.rate(person, track, activity,
                Optional.ofNullable(request.rating()).map(Rating::of));
        return new SongRatingResponse(request.ref(), rated
                .flatMap(song -> song.own(activity.map(Category::id)))
                .map(found -> found.rating().value())
                .orElse(null));
    }
}
