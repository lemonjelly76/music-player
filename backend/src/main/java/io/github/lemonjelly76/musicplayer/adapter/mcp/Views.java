package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.candidate.Candidate;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
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
 * What the tools return. Every album carries a {@code ref} the other tools accept back.
 */
final class Views {

    private Views() {
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

    record LibraryAlbumView(String id, AlbumView album, Integer rating, String notes, SortedSet<String> tags,
                            LocalDate addedOn) {

        static LibraryAlbumView from(LibraryAlbum entry) {
            return new LibraryAlbumView(entry.id().toString(), AlbumView.from(entry.album()),
                    entry.rating().map(Rating::value).orElse(null), entry.notes(), entry.tags(), entry.addedOn());
        }
    }

    record CandidateView(AlbumView album, double score, SortedSet<String> sources, List<String> seeds,
                         int bestSeedRating, Integer seedPlays) {

        static CandidateView from(Candidate candidate) {
            return new CandidateView(AlbumView.from(candidate.album()), candidate.score(), candidate.sources(),
                    candidate.seeds(), candidate.bestSeedRating(),
                    candidate.seedPlays() == 0 ? null : candidate.seedPlays());
        }
    }

    record PickView(String id, AlbumView album, String reason, String source, String verdict, String comment) {

        static PickView from(Recommendation pick) {
            return new PickView(pick.id().toString(), AlbumView.from(pick.album()), pick.reason(),
                    pick.source().name().toLowerCase(),
                    pick.feedback().map(feedback -> feedback.verdict().name()).orElse(null),
                    pick.feedback().map(feedback -> feedback.comment()).orElse(null));
        }
    }

    record ListView(String id, String name, String category, Instant createdAt, String playlist,
                    List<PickView> picks) {

        static ListView from(RecommendationList list, Map<String, String> categoryNames) {
            return new ListView(list.id().toString(), list.name(),
                    list.category().map(id -> categoryNames.get(id.toString())).orElse(null), list.createdAt(),
                    list.playlist().map(synced -> synced.provider().prefix() + ":" + synced.playlistId()).orElse(null),
                    list.picks().stream().map(PickView::from).toList());
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
