package io.github.lemonjelly76.musicplayer.domain.playlist;

import io.github.lemonjelly76.musicplayer.domain.album.Names;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Picks the catalogue's song for an artist and title from its search results, scoring each and taking the lowest:
 * <ul>
 * <li>by the artist, alone or first with guests (0), or as one of many, as compilations credit everyone (2);</li>
 * <li>the exact title (0), an edition such as "Something - Remastered 2009" (1), or another performance such as a
 * live or demo take (2).</li>
 * </ul>
 * Among equals, the search's order decides.
 */
public final class TrackMatch {

    private static final int NO_MATCH = Integer.MAX_VALUE;
    private static final Pattern OTHER_PERFORMANCE = Pattern.compile(
            "\\b(live|ao vivo|en vivo|en directo|demo|instrumental|remix|mix|acoustic|session|sessions|reprise|karaoke|"
                    + "rehearsal)\\b");

    private TrackMatch() {
    }

    public static Optional<Track> best(List<Track> found, String artist, String title) {
        String wantedArtist = Names.normalise(artist);
        String wantedTitle = Names.title(title);
        return found.stream()
                .filter(track -> score(track, wantedArtist, wantedTitle) != NO_MATCH)
                .min(Comparator.comparingInt(track -> score(track, wantedArtist, wantedTitle)));
    }

    private static int score(Track track, String wantedArtist, String wantedTitle) {
        int byArtist = artistScore(track.artist(), wantedArtist);
        int byTitle = titleScore(Names.title(track.title()), wantedTitle);
        return byArtist == NO_MATCH || byTitle == NO_MATCH ? NO_MATCH : byArtist + byTitle;
    }

    private static int artistScore(String credited, String wanted) {
        List<String> artists = Arrays.stream(credited.split(",")).map(Names::normalise).toList();
        if (Names.normalise(credited).equals(wanted) || artists.getFirst().equals(wanted)) {
            return 0;
        }
        return artists.contains(wanted) ? 2 : NO_MATCH;
    }

    private static int titleScore(String candidate, String wanted) {
        if (candidate.equals(wanted)) {
            return 0;
        }
        if (wanted.isEmpty() || !candidate.startsWith(wanted + " ")) {
            return NO_MATCH;
        }
        return OTHER_PERFORMANCE.matcher(candidate.substring(wanted.length())).find() ? 2 : 1;
    }
}
