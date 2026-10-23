package io.github.lemonjelly76.musicplayer.domain.candidate;

import io.github.lemonjelly76.musicplayer.domain.album.Names;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the person turned down for the activity, as it bears on new albums: artists they turned down, and what the
 * sources suggest for the albums they turned down, which the pool ranks lower.
 */
public record TurnedDown(Set<String> artists, Collection<Suggestion> near) {

    public static final TurnedDown NONE = new TurnedDown(Set.of(), List.of());

    public TurnedDown {
        artists = artists.stream().map(Names::normalise).collect(Collectors.toUnmodifiableSet());
        near = List.copyOf(near);
    }

    boolean artist(String name) {
        return artists.contains(Names.normalise(name));
    }
}
