package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.web.Requests.ChartPlaylistRequest;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.ChartPlaylistResponse;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.ChartResponse;
import io.github.lemonjelly76.musicplayer.application.ChartService;
import io.github.lemonjelly76.musicplayer.application.LibraryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.SongService;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartPlaylist;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The week's most played songs and albums, with the person's Mainstream ratings of them and their chart playlist.
 */
@RestController
@RequestMapping("/api")
class ChartController {

    private final PersonService people;
    private final ChartService charts;
    private final SongService songs;
    private final LibraryService library;
    private final MusicProviders providers;

    ChartController(PersonService people, ChartService charts, SongService songs, LibraryService library,
            MusicProviders providers) {
        this.people = people;
        this.charts = charts;
        this.songs = songs;
        this.library = library;
        this.providers = providers;
    }

    @GetMapping("/people/{id}/charts")
    ChartResponse charts(@PathVariable String id) {
        Person person = people.get(PersonId.of(id));
        ChartPlaylist playlist = charts.playlistFor(person);
        return ChartResponse.from(charts.chartFor(person), playlist, playlist.synced().flatMap(providers::link),
                songs.bySong(person.id()), library.byKey(person.id()), Links.forPerson(person, providers));
    }

    @PutMapping("/people/{id}/chart-playlist")
    ChartPlaylistResponse setPlaylist(@PathVariable String id, @RequestBody ChartPlaylistRequest request) {
        Person person = people.get(PersonId.of(id));
        if (request.enabled() == null) {
            throw new IllegalArgumentException("Say whether to turn the weekly playlist on or off");
        }
        return new ChartPlaylistResponse(charts.setPlaylistWanted(person, request.enabled()).wanted());
    }
}
