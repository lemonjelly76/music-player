package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.web.Requests.PlaylistSlotRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Requests.ServiceRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.CopyResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.PlaylistResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.StatusResponse;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlaylistCopies;
import io.github.lemonjelly76.musicplayer.application.PlaylistService;
import io.github.lemonjelly76.musicplayer.application.SongService;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final SongService songs;
    private final PlaylistCopies copies;

    PlaylistController(PersonService people, CategoryService categories, PlaylistService playlists,
            MusicProviders providers, SongService songs, PlaylistCopies copies) {
        this.people = people;
        this.categories = categories;
        this.playlists = playlists;
        this.providers = providers;
        this.songs = songs;
        this.copies = copies;
    }

    /**
     * The activity's playlists, newest first; those in the person's account link to it.
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
        Map<String, LibrarySong> rated = songs.bySong(person.id());
        return found.stream()
                .map(playlist -> PlaylistResponse.from(asSeenBy(person, playlist), providers::link, rated,
                        Links.forPerson(person, providers)))
                .toList();
    }

    /**
     * One in their account on a service they have since left is shown as not in their account.
     */
    private static Playlist asSeenBy(Person person, Playlist playlist) {
        return person.provider().filter(playlist::inAccountOn).isPresent() ? playlist : playlist.outOfAccount();
    }

    /**
     * Puts the playlist in the person's account as its activity's playlist 1, 2 or 3.
     */
    @PutMapping("/people/{id}/playlists/{playlistId}/slot")
    PlaylistResponse putInSlot(@PathVariable String id, @PathVariable String playlistId,
            @RequestBody PlaylistSlotRequest request) {
        if (request.slot() == null) {
            throw new IllegalArgumentException("Say which slot, 1 to " + Playlist.SLOTS);
        }
        Person person = people.get(PersonId.of(id));
        Playlist placed = playlists.putInSlot(person, PlaylistId.of(playlistId), request.slot());
        return PlaylistResponse.from(placed, providers::link, songs.bySong(person.id()),
                Links.forPerson(person, providers));
    }

    @GetMapping("/people/{id}/playlist-copies")
    List<CopyResponse> copies(@PathVariable String id) {
        Person person = people.get(PersonId.of(id));
        return copies.of(person).stream().map(copy -> CopyResponse.from(copy, providers::link)).toList();
    }

    /**
     * Copies the activity's newest playlist to the service named from now on, or stops when none is.
     */
    @PutMapping("/people/{id}/playlist-copy")
    StatusResponse copy(@PathVariable String id, @RequestParam(required = false) String category,
            @RequestBody ServiceRequest request) {
        Person person = people.get(PersonId.of(id));
        Optional<Category> activity = ActivityParameter.resolve(categories, category);
        if (request.service() == null || request.service().isBlank()) {
            copies.stop(person, activity);
            return new StatusResponse("no longer copied");
        }
        return new StatusResponse(copies.keep(person, activity, Provider.parse(request.service())));
    }
}
