package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.web.Responses.ListeningResponse;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlayService;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.plays.ListeningStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * What a person has been playing, for the app to show back to them. Like the model, the app sees nothing where
 * counting is off.
 */
@RestController
@RequestMapping("/api")
class PlaysController {

    static final int MAX_DAYS = 3650;
    static final int TOP = 20;

    private final PersonService people;
    private final CategoryService categories;
    private final PlayService plays;
    private final MusicProviders providers;

    PlaysController(PersonService people, CategoryService categories, PlayService plays, MusicProviders providers) {
        this.people = people;
        this.categories = categories;
        this.plays = plays;
        this.providers = providers;
    }

    /**
     * Tracks played, and the most-played albums and artists, over the last given days; an activity narrows them to
     * plays from its pick playlists.
     */
    @GetMapping("/people/{id}/plays")
    ListeningResponse listening(@PathVariable String id, @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "28") int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_DAYS);
        }
        Person person = people.get(PersonId.of(id));
        return plays.stats(person, ActivityParameter.resolve(categories, category), days)
                .map(totals -> ListeningResponse.from(true, days, ListeningStats.of(totals, TOP),
                        album -> providers.link(person, album)))
                .orElseGet(() -> ListeningResponse.from(false, days, ListeningStats.of(List.of(), TOP),
                        album -> Optional.empty()));
    }
}
