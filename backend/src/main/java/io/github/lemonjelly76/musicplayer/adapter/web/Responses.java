package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.candidate.Candidate;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.ArtistPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.ListeningStats;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.Function;

/**
 * What the app reads. Albums carry a link that opens them in the person's streaming app, when it carries them.
 */
final class Responses {

    private Responses() {
    }

    record PersonResponse(String id, String name, String service, boolean connected, boolean playTracking) {

        static PersonResponse from(Person person, boolean connected) {
            return new PersonResponse(person.id().toString(), person.name(),
                    person.provider().map(provider -> provider.prefix()).orElse(null), connected, person.playTracking());
        }
    }

    record CategoryResponse(String id, String name, String rules) {

        static CategoryResponse from(Category category) {
            return new CategoryResponse(category.id().toString(), category.name(), category.rules());
        }
    }

    record AlbumResponse(String ref, String artist, String title, Integer year, String link, String cover) {

        static AlbumResponse from(AlbumRef album, Function<AlbumRef, Optional<URI>> links) {
            String ref = album.ids().tidal().map(id -> "tidal:" + id)
                    .or(() -> album.ids().spotify().map(id -> "spotify:" + id))
                    .or(() -> album.ids().musicBrainz().map(id -> "mbid:" + id))
                    .orElse(album.label());
            return new AlbumResponse(ref, album.artist(), album.title(), album.year().orElse(null),
                    links.apply(album).map(URI::toString).orElse(null), album.cover().orElse(null));
        }
    }

    /**
     * A pick with the person's rating of it (1 to 3) for the list's activity, once they have given one.
     */
    record PickResponse(String id, AlbumResponse album, String reason, String source, Integer rating) {

        static PickResponse from(Recommendation pick, Optional<CategoryId> activity,
                Map<AlbumKey, LibraryAlbum> library, Function<AlbumRef, Optional<URI>> links) {
            Integer rating = Optional.ofNullable(library.get(pick.album().key()))
                    .flatMap(entry -> entry.rating(activity))
                    .map(found -> found.rating().value())
                    .orElse(null);
            return new PickResponse(pick.id().toString(), AlbumResponse.from(pick.album(), links), pick.reason(),
                    pick.source().name(), rating);
        }
    }

    /**
     * Albums to try; they are opened one by one, so the list has no playlist of its own.
     */
    record ListResponse(String id, String name, String categoryId, Instant createdAt, List<PickResponse> picks) {

        static ListResponse from(RecommendationList list, Map<AlbumKey, LibraryAlbum> library,
                Function<AlbumRef, Optional<URI>> links) {
            return new ListResponse(list.id().toString(), list.name(),
                    list.category().map(Object::toString).orElse(null), list.createdAt(),
                    list.picks().stream().map(pick -> PickResponse.from(pick, list.category(), library, links)).toList());
        }
    }

    /**
     * An album in the library for one activity, with its rating there; {@code general} when that rating is the
     * General one standing in for the activity's own.
     */
    record LibraryAlbumResponse(String id, AlbumResponse album, Integer rating, boolean general, String notes,
                                SortedSet<String> tags, LocalDate addedOn) {

        static LibraryAlbumResponse from(LibraryAlbum entry, Optional<CategoryId> activity,
                Function<AlbumRef, Optional<URI>> links) {
            Optional<ActivityRating> rating = entry.rating(activity);
            return new LibraryAlbumResponse(entry.id().toString(), AlbumResponse.from(entry.album(), links),
                    rating.map(found -> found.rating().value()).orElse(null),
                    activity.isPresent() && rating.map(ActivityRating::isGeneral).orElse(false), entry.notes(),
                    entry.tags(), entry.addedOn());
        }
    }

    /**
     * A catalogue search result with the person's rating of it for the activity, when the album is in their library.
     */
    record FoundResponse(AlbumResponse album, Integer rating, boolean general) {

        static FoundResponse from(AlbumRef album, Optional<LibraryAlbum> entry, Optional<CategoryId> activity,
                Function<AlbumRef, Optional<URI>> links) {
            Optional<ActivityRating> rating = entry.flatMap(found -> found.rating(activity));
            return new FoundResponse(AlbumResponse.from(album, links),
                    rating.map(found -> found.rating().value()).orElse(null),
                    activity.isPresent() && rating.map(ActivityRating::isGeneral).orElse(false));
        }
    }

    record RatingResponse(String albumRef, Integer rating) {
    }

    record CandidateResponse(AlbumResponse album, double score, SortedSet<String> sources, List<String> seeds) {

        static CandidateResponse from(Candidate candidate, Function<AlbumRef, Optional<URI>> links) {
            return new CandidateResponse(AlbumResponse.from(candidate.album(), links), candidate.score(),
                    candidate.sources(), candidate.seeds());
        }
    }

    /**
     * Songs to play straight through; only the newest for an activity has a {@code link}, as each new one replaces
     * it in the person's account.
     */
    record PlaylistResponse(String id, String name, String description, String categoryId, Instant createdAt,
                            String link, List<TrackResponse> tracks) {

        /**
         * Each song carries the person's rating of it for the playlist's activity.
         */
        static PlaylistResponse from(Playlist playlist, Optional<URI> link, Map<String, LibrarySong> rated,
                Function<Track, Optional<URI>> links) {
            return new PlaylistResponse(playlist.id().toString(), playlist.name(), playlist.description(),
                    playlist.category().map(Object::toString).orElse(null), playlist.createdAt(),
                    link.map(URI::toString).orElse(null),
                    playlist.tracks().stream()
                            .map(track -> TrackResponse.from(track, Optional.ofNullable(rated.get(track.songKey())),
                                    playlist.category(), links))
                            .toList());
        }
    }

    /**
     * A song as {@code ref} ({@code spotify:<id>} or {@code tidal:<id>}), with the person's rating of it (1 to 3) for
     * the activity; {@code general} when that is the General rating standing in for the activity's own.
     */
    record TrackResponse(String ref, String id, String artist, String title, String album, String cover, String link,
                         Integer rating, boolean general) {

        static TrackResponse from(Track track, Optional<LibrarySong> song, Optional<CategoryId> activity,
                Function<Track, Optional<URI>> links) {
            Optional<ActivityRating> rating = song.flatMap(found -> found.rating(activity));
            return new TrackResponse(track.provider().prefix() + ":" + track.id(), track.id(), track.artist(),
                    track.title(), track.album(), track.cover().orElse(null),
                    links.apply(track).map(URI::toString).orElse(null),
                    rating.map(found -> found.rating().value()).orElse(null),
                    activity.isPresent() && rating.map(ActivityRating::isGeneral).orElse(false));
        }
    }

    record SongRatingResponse(String ref, Integer rating) {
    }

    /**
     * What a person played over the last [days]; [tracking] is false, and everything empty, where counting is off.
     */
    record ListeningResponse(boolean tracking, int days, int tracks, int albumCount, List<PlayedAlbumResponse> albums,
            List<ArtistResponse> artists) {

        static ListeningResponse from(boolean tracking, int days, ListeningStats stats,
                Function<AlbumRef, Optional<URI>> links) {
            return new ListeningResponse(tracking, days, stats.tracks(), stats.albums(),
                    stats.topAlbums().stream().map(total -> PlayedAlbumResponse.from(total, links)).toList(),
                    stats.topArtists().stream().map(ArtistResponse::from).toList());
        }
    }

    record PlayedAlbumResponse(AlbumResponse album, int plays, Instant lastPlayed) {

        static PlayedAlbumResponse from(AlbumPlays total, Function<AlbumRef, Optional<URI>> links) {
            return new PlayedAlbumResponse(AlbumResponse.from(total.album(), links), total.plays(), total.lastPlayed());
        }
    }

    record ArtistResponse(String name, int plays, int albums) {

        static ArtistResponse from(ArtistPlays artist) {
            return new ArtistResponse(artist.artist(), artist.plays(), artist.albums());
        }
    }

    record TrackingResponse(boolean enabled, List<CategoryTracking> categories) {
    }

    record CategoryTracking(String categoryId, String name, boolean enabled, boolean overridden) {
    }

    record ServiceResponse(String service, boolean configured) {
    }

    record ConnectResponse(String service, String url) {
    }

    record ErrorResponse(String message) {
    }
}
