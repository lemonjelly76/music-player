package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.ServiceLink;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Where albums and songs open for one person: in their own service, and on the others the server is registered with.
 */
record Links(Function<AlbumRef, Optional<URI>> album, Function<AlbumRef, List<ServiceLink>> albumElsewhere,
             Function<Track, Optional<URI>> track, Function<Track, List<ServiceLink>> trackElsewhere) {

    static Links forPerson(Person person, MusicProviders providers) {
        return new Links(album -> providers.link(person, album), album -> providers.elsewhere(person, album),
                providers::link, providers::elsewhere);
    }
}
