package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.ServiceLink;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.AudioFormat;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.candidate.Candidate;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartPlaylist;
import io.github.lemonjelly76.musicplayer.domain.chart.ResolvedChart;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtist;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist.Slot;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistCopy;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.ArtistPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.ListeningStats;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Function;

/**
 * What the app reads. Albums carry a link that opens them in the person's streaming app, when it carries them.
 */
final class Responses {

    private Responses() {
    }

    /**
     * A person, whether they connected their own service, and the other services they connected too.
     */
    record PersonResponse(String id, String name, String service, boolean connected, boolean playTracking,
                          List<String> alsoConnected) {

        static PersonResponse from(Person person, boolean connected, List<Provider> alsoConnected) {
            return new PersonResponse(person.id().toString(), person.name(),
                    person.provider().map(provider -> provider.prefix()).orElse(null), connected, person.playTracking(),
                    alsoConnected.stream().map(Provider::prefix).toList());
        }
    }

    /**
     * Where an activity's newest playlist is copied; the link is there once it has been written.
     */
    record CopyResponse(String categoryId, String service, String link) {

        static CopyResponse from(PlaylistCopy copy, Function<SyncedPlaylist, Optional<URI>> playlistLinks) {
            return new CopyResponse(copy.category().map(Object::toString).orElse(null), copy.service().prefix(),
                    copy.playlistId().map(id -> new SyncedPlaylist(copy.service(), id)).flatMap(playlistLinks)
                            .map(URI::toString).orElse(null));
        }
    }

    record StatusResponse(String status) {
    }

    record CategoryResponse(String id, String name, String rules) {

        static CategoryResponse from(Category category) {
            return new CategoryResponse(category.id().toString(), category.name(), category.rules());
        }
    }

    /**
     * An album with the formats beyond the ordinary it can be played in (LOSSLESS, HI_RES, DOLBY_ATMOS), and where
     * it opens on the person's other services.
     */
    record AlbumResponse(String ref, String artist, String title, Integer year, String link, String cover,
                         List<String> formats, List<LinkResponse> elsewhere) {

        static AlbumResponse from(AlbumRef album, Links links) {
            String ref = album.ids().tidal().map(id -> "tidal:" + id)
                    .or(() -> album.ids().spotify().map(id -> "spotify:" + id))
                    .or(() -> album.ids().musicBrainz().map(id -> "mbid:" + id))
                    .orElse(album.label());
            return new AlbumResponse(ref, album.artist(), album.title(), album.year().orElse(null),
                    links.album().apply(album).map(URI::toString).orElse(null), album.cover().orElse(null),
                    names(album.formats()), LinkResponse.all(links.albumElsewhere().apply(album)));
        }
    }

    record LinkResponse(String service, String link) {

        static List<LinkResponse> all(List<ServiceLink> links) {
            return links.stream().map(found -> new LinkResponse(found.service().prefix(), found.link().toString()))
                    .toList();
        }
    }

    private static List<String> names(Set<AudioFormat> formats) {
        return formats.stream().map(Enum::name).toList();
    }

    /**
     * A pick with the person's rating of it (1 to 3) for the list's activity, once they have given one.
     */
    record PickResponse(String id, AlbumResponse album, String reason, String source, Integer rating) {

        static PickResponse from(Recommendation pick, Optional<CategoryId> activity,
                Map<AlbumKey, LibraryAlbum> library, Links links) {
            Integer rating = Optional.ofNullable(library.get(pick.album().key()))
                    .flatMap(entry -> entry.own(activity))
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
                Links links) {
            return new ListResponse(list.id().toString(), list.name(),
                    list.category().map(Object::toString).orElse(null), list.createdAt(),
                    list.picks().stream().map(pick -> PickResponse.from(pick, list.category(), library, links)).toList());
        }
    }

    /**
     * An album in the library, with its rating for the activity, when it has one there.
     */
    record LibraryAlbumResponse(String id, AlbumResponse album, Integer rating, String notes,
                                SortedSet<String> tags, LocalDate addedOn) {

        static LibraryAlbumResponse from(LibraryAlbum entry, Optional<CategoryId> activity,
                Links links) {
            return new LibraryAlbumResponse(entry.id().toString(), AlbumResponse.from(entry.album(), links),
                    entry.own(activity).map(found -> found.rating().value()).orElse(null), entry.notes(),
                    entry.tags(), entry.addedOn());
        }
    }

    /**
     * A catalogue search result with the person's rating of it for the activity, when the album is in their library.
     */
    record FoundResponse(AlbumResponse album, Integer rating) {

        static FoundResponse from(AlbumRef album, Optional<LibraryAlbum> entry, Optional<CategoryId> activity,
                Links links) {
            return new FoundResponse(AlbumResponse.from(album, links),
                    entry.flatMap(found -> found.own(activity)).map(found -> found.rating().value()).orElse(null));
        }
    }

    record RatingResponse(String albumRef, Integer rating) {
    }

    record CandidateResponse(AlbumResponse album, double score, SortedSet<String> sources, List<String> seeds) {

        static CandidateResponse from(Candidate candidate, Links links) {
            return new CandidateResponse(AlbumResponse.from(candidate.album(), links), candidate.score(),
                    candidate.sources(), candidate.seeds());
        }
    }

    /**
     * Songs to play straight through; only the newest for an activity has a {@code link}, as each new one replaces
     * it in the person's account.
     */
    /**
     * A playlist and, while it is in the person's account, its slot there and a link to play it.
     */
    record PlaylistResponse(String id, String name, String description, String categoryId, Instant createdAt,
                            Integer slot, String link, List<TrackResponse> tracks) {

        /**
         * Each song carries the person's rating of it for the playlist's activity.
         */
        static PlaylistResponse from(Playlist playlist, Function<SyncedPlaylist, Optional<URI>> playlistLinks,
                Map<String, LibrarySong> rated, Links links) {
            Optional<URI> link = playlist.slot().flatMap(slot -> playlist.synced()).flatMap(playlistLinks);
            return new PlaylistResponse(playlist.id().toString(), playlist.name(), playlist.description(),
                    playlist.category().map(Object::toString).orElse(null), playlist.createdAt(),
                    playlist.slot().map(Slot::number).orElse(null), link.map(URI::toString).orElse(null),
                    playlist.tracks().stream()
                            .map(track -> TrackResponse.from(track, Optional.ofNullable(rated.get(track.songKey())),
                                    playlist.category(), links))
                            .toList());
        }
    }

    /**
     * A song as {@code ref} ({@code spotify:<id>} or {@code tidal:<id>}), with the person's rating of it (1 to 3) for
     * the activity, when it has one there.
     */
    record TrackResponse(String ref, String id, String artist, String title, String album, String cover, String link,
                         Integer rating, List<String> formats, List<LinkResponse> elsewhere) {

        static TrackResponse from(Track track, Optional<LibrarySong> song, Optional<CategoryId> activity,
                Links links) {
            return new TrackResponse(track.provider().prefix() + ":" + track.id(), track.id(), track.artist(),
                    track.title(), track.album(), track.cover().orElse(null),
                    links.track().apply(track).map(URI::toString).orElse(null),
                    song.flatMap(found -> found.own(activity)).map(found -> found.rating().value()).orElse(null),
                    names(track.formats()), LinkResponse.all(links.trackElsewhere().apply(track)));
        }
    }

    record SongRatingResponse(String ref, Integer rating) {
    }

    /**
     * An artist with the person's rating of them for the activity.
     */
    record RatedArtistResponse(String id, String name, Integer rating) {

        static RatedArtistResponse from(LibraryArtist artist, Optional<CategoryId> activity) {
            return new RatedArtistResponse(artist.id().toString(), artist.name(),
                    artist.own(activity).map(found -> found.rating().value()).orElse(null));
        }
    }

    record ArtistRatingResponse(String artist, Integer rating) {
    }

    /**
     * The week's chart, its songs and albums at their chart places with the person's Mainstream rating of each, and
     * their chart playlist; {@code updatedAt} is null, and the lists empty, until the chart is first fetched.
     */
    record ChartResponse(String country, Instant updatedAt, boolean playlistWanted, String playlistLink,
                         List<ChartSongResponse> songs, List<ChartAlbumResponse> albums) {

        static ChartResponse from(Optional<ResolvedChart> chart, ChartPlaylist playlist, Optional<URI> playlistLink,
                Map<String, LibrarySong> songs, Map<AlbumKey, LibraryAlbum> albums,
                Links links) {
            Optional<CategoryId> mainstream = Optional.empty();
            return new ChartResponse(chart.map(ResolvedChart::country).orElse(null),
                    chart.map(ResolvedChart::fetchedAt).orElse(null), playlist.wanted(),
                    playlistLink.map(URI::toString).orElse(null),
                    chart.map(ResolvedChart::songs).orElse(List.of()).stream()
                            .map(song -> new ChartSongResponse(song.position(), TrackResponse.from(song.track(),
                                    Optional.ofNullable(songs.get(song.track().songKey())), mainstream, links)))
                            .toList(),
                    chart.map(ResolvedChart::albums).orElse(List.of()).stream()
                            .map(found -> new ChartAlbumResponse(found.position(), FoundResponse.from(found.album(),
                                    Optional.ofNullable(albums.get(found.album().key())), mainstream, links)))
                            .toList());
        }
    }

    record ChartSongResponse(int position, TrackResponse song) {
    }

    record ChartAlbumResponse(int position, FoundResponse album) {
    }

    record ChartPlaylistResponse(boolean enabled) {
    }

    /**
     * What a person played over the last [days]; [tracking] is false, and everything empty, where counting is off.
     */
    record ListeningResponse(boolean tracking, int days, int tracks, int albumCount, List<PlayedAlbumResponse> albums,
            List<ArtistResponse> artists) {

        static ListeningResponse from(boolean tracking, int days, ListeningStats stats,
                Links links) {
            return new ListeningResponse(tracking, days, stats.tracks(), stats.albums(),
                    stats.topAlbums().stream().map(total -> PlayedAlbumResponse.from(total, links)).toList(),
                    stats.topArtists().stream().map(ArtistResponse::from).toList());
        }
    }

    record PlayedAlbumResponse(AlbumResponse album, int plays, Instant lastPlayed) {

        static PlayedAlbumResponse from(AlbumPlays total, Links links) {
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
