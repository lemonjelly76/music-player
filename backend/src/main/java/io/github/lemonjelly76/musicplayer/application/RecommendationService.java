package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.application.AlbumResolver.Resolution;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.port.RecommendationRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Feedback;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationId;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Verdict;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Step 6 of the pipeline and the feedback loop: checks every pick the model makes against the catalogue and the
 * person's history, keeps the list, syncs it to their streaming service as a playlist, and records their verdicts.
 */
public class RecommendationService {

    private final RecommendationRepository repository;
    private final AlbumResolver resolver;
    private final LibraryService library;
    private final MusicProviders providers;
    private final PersonService people;
    private final Clock clock;

    public RecommendationService(RecommendationRepository repository, AlbumResolver resolver, LibraryService library,
            MusicProviders providers, PersonService people, Clock clock) {
        this.repository = repository;
        this.resolver = resolver;
        this.library = library;
        this.providers = providers;
        this.people = people;
        this.clock = clock;
    }

    public Saved save(Person person, Optional<Category> category, List<PickRequest> requests) {
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("A list needs at least one pick");
        }
        Optional<MusicProvider> service = providers.forPerson(person);
        Set<AlbumKey> taken = new HashSet<>(library.keys(person.id()));
        Set<AlbumKey> turnedDown = turnedDown(person, category.map(Category::id));
        List<Recommendation> picks = new ArrayList<>();
        List<Dropped> dropped = new ArrayList<>();
        for (PickRequest request : requests) {
            Optional<String> repeat = resolver.named(request.albumRef())
                    .flatMap(named -> whyNotOffered(named.key(), taken, turnedDown));
            if (repeat.isPresent()) {
                dropped.add(new Dropped(request.albumRef(), repeat.get()));
                continue;
            }
            Resolution resolution = resolver.resolve(request.albumRef(), service);
            if (resolution.album().isEmpty()) {
                dropped.add(new Dropped(request.albumRef(), resolution.problem()));
                continue;
            }
            AlbumRef album = resolution.album().get();
            Optional<String> why = whyNotOffered(album.key(), taken, turnedDown);
            if (why.isPresent()) {
                dropped.add(new Dropped(request.albumRef(), why.get()));
            } else {
                taken.add(album.key());
                picks.add(Recommendation.create(album, request.reason(), request.source()));
            }
        }
        if (picks.isEmpty()) {
            throw new IllegalArgumentException("No pick could be kept: " + dropped);
        }
        RecommendationList list = RecommendationList.create(person.id(), category, picks, clock.instant());
        repository.save(list);
        return sync(person, list, service, dropped);
    }

    public List<RecommendationList> lists(Person person, Optional<Category> category, int limit) {
        return repository.findByPerson(person.id(), category.map(Category::id), limit);
    }

    /**
     * Every list of the person's, whatever its category, newest first.
     */
    public List<RecommendationList> history(Person person, int limit) {
        return repository.findByPerson(person.id(), Optional.empty(), limit);
    }

    /**
     * Records the verdict on a pick; a liked pick joins the person's library, as any album they like does.
     */
    public Judged feedback(RecommendationId pickId, Verdict verdict, String comment) {
        RecommendationList list = repository.findContaining(pickId)
                .orElseThrow(() -> new NotFoundException("No recommendation with id " + pickId));
        Recommendation pick = list.pick(pickId).orElseThrow();
        Feedback feedback = new Feedback(verdict, comment, clock.instant());
        repository.saveFeedback(pickId, feedback);
        boolean addedToLibrary = false;
        if (verdict == Verdict.LIKED) {
            Person person = people.get(list.person());
            addedToLibrary = !library.add(person, pick.album(), Optional.empty(), comment, List.of("ai-pick"))
                    .alreadyInLibrary();
        }
        return new Judged(list, pick.withFeedback(feedback), addedToLibrary);
    }

    /**
     * Checked by name before any lookup too, so a repeat costs no catalogue call and gets the right reason.
     */
    private static Optional<String> whyNotOffered(AlbumKey key, Set<AlbumKey> taken, Set<AlbumKey> turnedDown) {
        if (turnedDown.contains(key)) {
            return Optional.of("already turned down or already known");
        }
        if (taken.contains(key)) {
            return Optional.of("already in the library or in this list");
        }
        return Optional.empty();
    }

    private Saved sync(Person person, RecommendationList list, Optional<MusicProvider> service, List<Dropped> dropped) {
        if (service.isEmpty()) {
            return new Saved(list, dropped, "not synced: " + person.name() + " has no streaming service linked");
        }
        MusicProvider provider = service.get();
        Optional<String> previous = previousPlaylist(person, list);
        List<String> albumIds = list.picks().stream()
                .map(pick -> pick.album().ids().get(provider.name()).orElseThrow())
                .toList();
        try {
            String playlistId = provider.replacePlaylist(person.id(), previous, list.name(),
                    "Picked for " + person.name() + " on " + LocalDate.now(clock), albumIds);
            RecommendationList synced = list.syncedTo(new SyncedPlaylist(provider.name(), playlistId));
            repository.save(synced);
            return new Saved(synced, dropped, "synced to " + provider.name().prefix() + " as " + list.name());
        } catch (MusicProviderUnavailableException e) {
            return new Saved(list, dropped, "saved but not synced: " + e.getMessage());
        }
    }

    private Optional<String> previousPlaylist(Person person, RecommendationList list) {
        return repository.findByPerson(person.id(), list.category(), 10).stream()
                .filter(earlier -> !earlier.id().equals(list.id()) && earlier.name().equals(list.name()))
                .flatMap(earlier -> earlier.playlist().stream())
                .map(SyncedPlaylist::playlistId)
                .findFirst();
    }

    private Set<AlbumKey> turnedDown(Person person, Optional<CategoryId> category) {
        Set<AlbumKey> keys = new HashSet<>();
        for (RecommendationList list : history(person, 200)) {
            for (Recommendation pick : list.picks()) {
                if (pick.has(Verdict.ALREADY_KNOW) || (list.category().equals(category) && pick.has(Verdict.NOT_FOR_ME))) {
                    keys.add(pick.album().key());
                }
            }
        }
        return keys;
    }

    public record PickRequest(String albumRef, String reason, PickSource source) {
    }

    public record Dropped(String albumRef, String why) {

        @Override
        public String toString() {
            return albumRef + " (" + why + ")";
        }
    }

    public record Saved(RecommendationList list, List<Dropped> dropped, String sync) {
    }

    public record Judged(RecommendationList list, Recommendation pick, boolean addedToLibrary) {
    }
}
