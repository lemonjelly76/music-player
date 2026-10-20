package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumKey;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.github.lemonjelly76.musicplayer.domain.plays.Play;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayBatch;
import io.github.lemonjelly76.musicplayer.domain.plays.PlayTracking;
import io.github.lemonjelly76.musicplayer.domain.port.ListeningHistory;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlayRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlayRepository.CategorisedPlay;
import io.github.lemonjelly76.musicplayer.domain.port.RecommendationRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Counts what people play as a preference signal, only where they have left counting on.
 */
public class PlayService {

    private static final System.Logger LOG = System.getLogger(PlayService.class.getName());

    private final PlayRepository plays;
    private final PersonRepository people;
    private final RecommendationRepository lists;
    private final List<ListeningHistory> histories;
    private final Clock clock;

    public PlayService(PlayRepository plays, PersonRepository people, RecommendationRepository lists,
            List<ListeningHistory> histories, Clock clock) {
        this.plays = plays;
        this.people = people;
        this.lists = lists;
        this.histories = List.copyOf(histories);
        this.clock = clock;
    }

    public PlayTracking tracking(Person person) {
        return new PlayTracking(person.playTracking(), plays.trackingOverrides(person.id()));
    }

    /**
     * Turns counting on or off for the person, or for one category of theirs; for a category, an empty setting
     * makes it follow the person's again.
     */
    public PlayTracking setTracking(Person person, Optional<Category> category, Optional<Boolean> enabled) {
        if (category.isPresent()) {
            plays.setTrackingOverride(person.id(), category.get().id(), enabled);
            return tracking(person);
        }
        boolean on = enabled.orElseThrow(() -> new IllegalArgumentException("Say whether to turn counting on or off"));
        Person changed = person.withPlayTracking(on);
        people.save(changed);
        return tracking(changed);
    }

    /**
     * Most-played albums over the last given days, or empty when counting is off, so the model never sees counts
     * the person asked not to be used.
     */
    public Optional<List<AlbumPlays>> stats(Person person, Optional<Category> category, int days) {
        if (!tracking(person).enabledFor(category.map(Category::id))) {
            return Optional.empty();
        }
        Instant since = clock.instant().minus(Duration.ofDays(days));
        return Optional.of(plays.albumTotals(person.id(), category.map(Category::id), since));
    }

    public Map<AlbumKey, Integer> playsByAlbum(Person person, Optional<Category> category, int days) {
        return stats(person, category, days).orElse(List.of()).stream()
                .collect(Collectors.toMap(total -> total.album().key(), AlbumPlays::plays, Integer::sum));
    }

    public void clear(Person person, Optional<Category> category) {
        plays.clear(person.id(), category.map(Category::id));
    }

    /**
     * Reads everyone's new plays from each listening history that covers them. A history that fails is logged and
     * tried again next time from the same cursor.
     */
    public int collect() {
        int saved = 0;
        for (Person person : people.findAll()) {
            PlayTracking tracking = tracking(person);
            if (!tracking.anyEnabled()) {
                continue;
            }
            for (ListeningHistory history : histories) {
                if (history.covers(person)) {
                    saved += collect(person, tracking, history);
                }
            }
        }
        return saved;
    }

    private int collect(Person person, PlayTracking tracking, ListeningHistory history) {
        try {
            PlayBatch batch = history.playsSince(person, plays.cursor(person.id(), history.source()));
            List<CategorisedPlay> kept = batch.plays().stream()
                    .map(play -> new CategorisedPlay(play, categoryOf(play)))
                    .filter(play -> tracking.enabledFor(play.category()))
                    .toList();
            plays.add(person.id(), kept);
            batch.nextCursor().ifPresent(cursor -> plays.saveCursor(person.id(), history.source(), cursor));
            return kept.size();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not read " + history.source() + " plays for " + person.name(), e);
            return 0;
        }
    }

    private Optional<CategoryId> categoryOf(Play play) {
        return play.playlistId().flatMap(lists::findByPlaylist).flatMap(RecommendationList::category);
    }
}
