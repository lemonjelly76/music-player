package io.github.lemonjelly76.musicplayer.domain.candidate;

import io.github.lemonjelly76.musicplayer.domain.album.Names;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtist;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the person turned down for the activity, as it bears on new albums: artists turned down themselves, whose
 * albums are left out; artists of albums turned down, and what the sources suggest for those albums, which rank
 * lower.
 */
public record TurnedDown(Set<String> artists, Set<String> albumArtists, Collection<Suggestion> near) {

    public static final TurnedDown NONE = new TurnedDown(Set.of(), Set.of(), List.of());

    public TurnedDown {
        artists = normalised(artists);
        albumArtists = normalised(albumArtists);
        near = List.copyOf(near);
    }

    private static Set<String> normalised(Set<String> names) {
        return names.stream().map(Names::normalise).collect(Collectors.toUnmodifiableSet());
    }

    boolean leftOut(String credit) {
        return LibraryArtist.credited(credit).stream().anyMatch(artists::contains);
    }

    boolean albumArtist(String name) {
        return albumArtists.contains(Names.normalise(name));
    }
}
