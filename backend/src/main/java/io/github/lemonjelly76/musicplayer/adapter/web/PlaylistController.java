package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.web.Responses.PlaylistResponse;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlaylistService;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The song playlists Claude made, for the app to show and play.
 */
@RestController
@RequestMapping("/api")
class PlaylistController {

    static final int MAX_LIMIT = 20;

    private final PersonService people;
    private final CategoryService categories;
    private final PlaylistService playlists;
    private final MusicProviders providers;

    PlaylistController(PersonService people, CategoryService categories, PlaylistService playlists,
            MusicProviders providers) {
        this.people = people;
        this.categories = categories;
        this.playlists = playlists;
        this.providers = providers;
    }

    /**
     * The activity's playlists, newest first. Each replaces the last in the person's account, so only the newest
     * links to one.
     */
    @GetMapping("/people/{id}/playlists")
    List<PlaylistResponse> playlists(@PathVariable String id, @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "5") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        Person person = people.get(PersonId.of(id));
        List<Playlist> found = playlists.latest(person,
                ActivityParameter.resolve(categories, category).map(Category::id), limit);
        return IntStream.range(0, found.size())
                .mapToObj(index -> PlaylistResponse.from(found.get(index),
                        index == 0 ? found.get(index).synced().flatMap(providers::link) : Optional.empty(),
                        providers::link))
                .toList();
    }
}
