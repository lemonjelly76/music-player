package io.github.lemonjelly76.musicplayer.adapter.mcp;

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
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;

/**
 * What the tools return. Every album carries a {@code ref} the other tools accept back, and every activity is named,
 * with General for ratings that are not for one activity.
 */
final class Views {

    static final String GENERAL = "General";

    private Views() {
    }

    static String activityName(Optional<CategoryId> category, Map<CategoryId, String> names) {
        return category.map(id -> names.getOrDefault(id, "?")).orElse(GENERAL);
    }

    record PersonView(String id, String name, String service, Boolean connected, boolean playTracking,
                      String lastFmUser) {

        static PersonView from(Person person, Optional<Boolean> connected) {
            return new PersonView(person.id().toString(), person.name(),
                    person.provider().map(provider -> provider.prefix()).orElse(null), connected.orElse(null),
                    person.playTracking(), person.lastFmUser().orElse(null));
        }
    }

    record AlbumView(String ref, String artist, String title, Integer year) {

        static AlbumView from(AlbumRef album) {
            return new AlbumView(ref(album), album.artist(), album.title(), album.year().orElse(null));
        }

        static String ref(AlbumRef album) {
            return album.ids().tidal().map(id -> "tidal:" + id)
                    .or(() -> album.ids().spotify().map(id -> "spotify:" + id))
                    .or(() -> album.ids().musicBrainz().map(id -> "mbid:" + id))
                    .orElse(album.label());
        }
    }

    record RatingView(String activity, String rating, String comment, Instant ratedAt) {

        static RatingView from(ActivityRating rating, Map<CategoryId, String> names) {
            return new RatingView(activityName(rating.category(), names), rating.rating().word(), rating.comment(),
                    rating.ratedAt());
        }
    }

    record LibraryAlbumView(String id, AlbumView album, List<RatingView> ratings, String notes,
                            SortedSet<String> tags, LocalDate addedOn) {

        static LibraryAlbumView from(LibraryAlbum entry, Map<CategoryId, String> names) {
            return new LibraryAlbumView(entry.id().toString(), AlbumView.from(entry.album()),
                    entry.ratings().stream().map(rating -> RatingView.from(rating, names)).toList(), entry.notes(),
                    entry.tags(), entry.addedOn());
        }
    }

    record CandidateView(AlbumView album, double score, SortedSet<String> sources, List<String> seeds,
                         Integer seedPlays) {

        static CandidateView from(Candidate candidate) {
            return new CandidateView(AlbumView.from(candidate.album()), candidate.score(), candidate.sources(),
                    candidate.seeds(), candidate.seedPlays() == 0 ? null : candidate.seedPlays());
        }
    }

    /**
     * A pick with the person's rating of it for the list's activity, once they have given one.
     */
    record PickView(String id, AlbumView album, String reason, String source, String rating) {

        static PickView from(Recommendation pick, Optional<CategoryId> activity, Map<AlbumKey, LibraryAlbum> library) {
            String rating = Optional.ofNullable(library.get(pick.album().key()))
                    .flatMap(entry -> entry.rating(activity))
                    .map(found -> found.rating().word())
                    .orElse(null);
            return new PickView(pick.id().toString(), AlbumView.from(pick.album()), pick.reason(),
                    pick.source().name().toLowerCase(), rating);
        }
    }

    record ListView(String id, String name, String activity, Instant createdAt, String playlist,
                    List<PickView> picks) {

        static ListView from(RecommendationList list, Map<CategoryId, String> names,
                Map<AlbumKey, LibraryAlbum> library) {
            return new ListView(list.id().toString(), list.name(), activityName(list.category(), names), list.createdAt(),
                    list.playlist().map(synced -> synced.provider().prefix() + ":" + synced.playlistId()).orElse(null),
                    list.picks().stream().map(pick -> PickView.from(pick, list.category(), library)).toList());
        }
    }

    /**
     * A playlist with each song's rating for its activity (love, ok or dislike), where the person has given one.
     */
    record PlaylistView(String name, String activity, String description, Instant createdAt, boolean synced,
                        List<PlaylistSongView> songs) {

        static PlaylistView from(Playlist playlist, String activity, Map<String, LibrarySong> rated) {
            return new PlaylistView(playlist.name(), activity, playlist.description(), playlist.createdAt(),
                    playlist.synced().isPresent(),
                    playlist.tracks().stream()
                            .map(track -> new PlaylistSongView(track.label(), track.album(),
                                    Optional.ofNullable(rated.get(track.songKey()))
                                            .flatMap(song -> song.rating(playlist.category()))
                                            .map(rating -> rating.rating().word())
                                            .orElse(null)))
                            .toList());
        }
    }

    record PlaylistSongView(String song, String album, String rating) {
    }

    record SongView(String song, String album, String ref, List<RatingView> ratings) {

        static SongView from(LibrarySong song, Map<CategoryId, String> names) {
            Track track = song.track();
            return new SongView(track.label(), track.album(), track.provider().prefix() + ":" + track.id(),
                    song.ratings().stream().map(rating -> RatingView.from(rating, names)).toList());
        }
    }

    record CategoryView(String id, String name, String rules) {

        static CategoryView from(Category category) {
            return new CategoryView(category.id().toString(), category.name(), category.rules());
        }
    }

    record PlaysView(AlbumView album, int plays, Instant lastPlayed) {

        static PlaysView from(AlbumPlays total) {
            return new PlaysView(AlbumView.from(total.album()), total.plays(), total.lastPlayed());
        }
    }
}
