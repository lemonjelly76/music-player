package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.candidate.Candidate;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    record AlbumResponse(String ref, String artist, String title, Integer year, String link) {

        static AlbumResponse from(AlbumRef album, Function<AlbumRef, Optional<URI>> links) {
            String ref = album.ids().tidal().map(id -> "tidal:" + id)
                    .or(() -> album.ids().spotify().map(id -> "spotify:" + id))
                    .or(() -> album.ids().musicBrainz().map(id -> "mbid:" + id))
                    .orElse(album.label());
            return new AlbumResponse(ref, album.artist(), album.title(), album.year().orElse(null),
                    links.apply(album).map(URI::toString).orElse(null));
        }
    }

    record PickResponse(String id, AlbumResponse album, String reason, String source, String verdict, String comment) {

        static PickResponse from(Recommendation pick, Function<AlbumRef, Optional<URI>> links) {
            return new PickResponse(pick.id().toString(), AlbumResponse.from(pick.album(), links), pick.reason(),
                    pick.source().name(), pick.feedback().map(feedback -> feedback.verdict().name()).orElse(null),
                    pick.feedback().map(feedback -> feedback.comment()).orElse(null));
        }
    }

    record ListResponse(String id, String name, String categoryId, Instant createdAt, String playlistLink,
                        List<PickResponse> picks) {

        static ListResponse from(RecommendationList list, Optional<URI> playlistLink,
                Function<AlbumRef, Optional<URI>> links) {
            return new ListResponse(list.id().toString(), list.name(),
                    list.category().map(Object::toString).orElse(null), list.createdAt(),
                    playlistLink.map(URI::toString).orElse(null),
                    list.picks().stream().map(pick -> PickResponse.from(pick, links)).toList());
        }
    }

    record LibraryAlbumResponse(String id, AlbumResponse album, Integer rating, String notes, SortedSet<String> tags,
                                LocalDate addedOn) {

        static LibraryAlbumResponse from(LibraryAlbum entry, Function<AlbumRef, Optional<URI>> links) {
            return new LibraryAlbumResponse(entry.id().toString(), AlbumResponse.from(entry.album(), links),
                    entry.rating().map(Rating::value).orElse(null), entry.notes(), entry.tags(), entry.addedOn());
        }
    }

    record CandidateResponse(AlbumResponse album, double score, SortedSet<String> sources, List<String> seeds) {

        static CandidateResponse from(Candidate candidate, Function<AlbumRef, Optional<URI>> links) {
            return new CandidateResponse(AlbumResponse.from(candidate.album(), links), candidate.score(),
                    candidate.sources(), candidate.seeds());
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
