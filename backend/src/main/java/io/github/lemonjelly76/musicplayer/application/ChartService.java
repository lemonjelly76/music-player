package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.album.Names;
import io.github.lemonjelly76.musicplayer.domain.chart.Chart;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartEntry;
import io.github.lemonjelly76.musicplayer.domain.chart.ChartPlaylist;
import io.github.lemonjelly76.musicplayer.domain.chart.ResolvedChart;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.ChartRepository;
import io.github.lemonjelly76.musicplayer.domain.port.ChartSource;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The most played songs and albums this week, kept without the model: the chart is fetched once a day and found on
 * each streaming service, and each person who wants it gets the songs as a playlist in their account each Monday.
 */
public class ChartService {

    public static final String PLAYLIST_NAME = "AI Picks — Popular this week";
    private static final System.Logger LOG = System.getLogger(ChartService.class.getName());
    private static final Duration STALE_AFTER = Duration.ofHours(20);

    private final ChartSource source;
    private final ChartRepository repository;
    private final MusicProviders providers;
    private final PersonRepository people;
    private final Clock clock;
    private final Duration lookupPause;

    /**
     * {@code lookupPause} spaces out the hundred or so catalogue lookups a chart takes, as services limit how fast an app
     * may ask; it runs in the background, so taking a minute does not matter.
     */
    public ChartService(ChartSource source, ChartRepository repository, MusicProviders providers,
            PersonRepository people, Clock clock, Duration lookupPause) {
        this.source = source;
        this.repository = repository;
        this.providers = providers;
        this.people = people;
        this.clock = clock;
        this.lookupPause = lookupPause;
    }

    /**
     * Fetches the chart and finds it on each service whose copy is missing or a day old. A service that fails keeps
     * its last copy and is tried again next time.
     */
    public void refreshIfStale() {
        List<MusicProvider> stale = providers.configured().stream().filter(this::stale).toList();
        if (stale.isEmpty()) {
            return;
        }
        Chart chart = source.latest();
        for (MusicProvider provider : stale) {
            try {
                repository.save(resolve(chart, provider));
            } catch (MusicProviderUnavailableException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not find the chart on " + provider.name().prefix(), e);
            }
        }
    }

    /**
     * The chart as found on the person's service, or on the first one set up when they have none.
     */
    public Optional<ResolvedChart> chartFor(Person person) {
        return providers.catalogueFor(Optional.of(person)).flatMap(provider -> repository.find(provider.name()));
    }

    /**
     * Writes this week's playlist for everyone who wants it, has an account connected and has not had it since
     * Monday, replacing last week's.
     */
    public int writeDuePlaylists() {
        Instant weekStart = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(clock.getZone()).toInstant();
        int written = 0;
        for (Person person : people.findAll()) {
            ChartPlaylist playlist = repository.playlistFor(person.id());
            boolean due = playlist.wanted() && playlist.writtenAt().map(at -> at.isBefore(weekStart)).orElse(true);
            Optional<MusicProvider> service = providers.forPerson(person).filter(found -> found.connected(person.id()));
            if (due && service.isPresent() && write(person, playlist, service.get())) {
                written++;
            }
        }
        return written;
    }

    public ChartPlaylist playlistFor(Person person) {
        return repository.playlistFor(person.id());
    }

    /**
     * Turns the weekly playlist on or off for the person; one already in their account stays there.
     */
    public ChartPlaylist setPlaylistWanted(Person person, boolean wanted) {
        ChartPlaylist changed = repository.playlistFor(person.id()).wanted(wanted);
        repository.save(changed);
        return changed;
    }

    private boolean stale(MusicProvider provider) {
        return repository.find(provider.name())
                .map(chart -> chart.fetchedAt().isBefore(clock.instant().minus(STALE_AFTER)))
                .orElse(true);
    }

    private ResolvedChart resolve(Chart chart, MusicProvider provider) {
        List<ResolvedChart.Song> songs = new ArrayList<>();
        for (ChartEntry entry : chart.songs()) {
            pause();
            provider.findTrack(entry.artist(), Names.withoutGuests(entry.title()))
                    .ifPresent(track -> songs.add(new ResolvedChart.Song(entry.position(), track)));
        }
        List<ResolvedChart.Album> albums = new ArrayList<>();
        for (ChartEntry entry : chart.albums()) {
            pause();
            AlbumRef album = provider.findAlbum(entry.artist(), entry.title())
                    .orElseGet(() -> AlbumRef.of(entry.artist(), entry.title()));
            albums.add(new ResolvedChart.Album(entry.position(), album));
        }
        return new ResolvedChart(provider.name(), chart.country(), clock.instant(), songs, albums);
    }

    private void pause() {
        if (lookupPause.isZero()) {
            return;
        }
        try {
            Thread.sleep(lookupPause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MusicProviderUnavailableException("Interrupted while finding the chart", e);
        }
    }

    private boolean write(Person person, ChartPlaylist playlist, MusicProvider service) {
        Optional<ResolvedChart> chart = repository.find(service.name());
        if (chart.isEmpty() || chart.get().songs().isEmpty()) {
            return false;
        }
        List<String> trackIds = chart.get().songs().stream().map(song -> song.track().id()).toList();
        Optional<String> previous = playlist.synced()
                .filter(synced -> synced.provider() == service.name())
                .map(SyncedPlaylist::playlistId);
        String description = "The " + chart.get().country().toUpperCase() + "'s " + trackIds.size()
                + " most played songs this week, refreshed every Monday";
        try {
            String playlistId = service.replacePlaylist(person.id(), previous, PLAYLIST_NAME, description, trackIds);
            repository.save(playlist.writtenTo(new SyncedPlaylist(service.name(), playlistId), clock.instant()));
            return true;
        } catch (MusicProviderUnavailableException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not write the chart playlist for " + person.name(), e);
            return false;
        }
    }
}
