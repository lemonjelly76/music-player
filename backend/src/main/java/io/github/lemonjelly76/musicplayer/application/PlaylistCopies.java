package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistCopy;
import io.github.lemonjelly76.musicplayer.domain.playlist.Track;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistCopyRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistRepository;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList.SyncedPlaylist;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps an activity's newest playlist on a second service as well as the person's own, for those who use both: Tidal
 * to listen in the best quality, say, and Spotify for Google Home voice and play counts. Each song is found again on
 * that service; one it does not have is left out of the copy.
 */
public class PlaylistCopies {

    private static final System.Logger LOG = System.getLogger(PlaylistCopies.class.getName());

    private final PlaylistCopyRepository copies;
    private final PlaylistRepository playlists;
    private final MusicProviders providers;

    public PlaylistCopies(PlaylistCopyRepository copies, PlaylistRepository playlists, MusicProviders providers) {
        this.copies = copies;
        this.playlists = playlists;
        this.providers = providers;
    }

    public List<PlaylistCopy> of(Person person) {
        return copies.forPerson(person.id());
    }

    /**
     * Copies the activity's playlists to the service from now on, starting with its newest; says how that went, or
     * that the next playlist will be the first.
     */
    public String keep(Person person, Optional<Category> activity, Provider service) {
        if (person.provider().equals(Optional.of(service))) {
            throw new IllegalArgumentException(person.name() + "'s playlists are already on " + service.prefix());
        }
        if (!providers.get(service).connected(person.id())) {
            throw new IllegalArgumentException("Connect " + service.prefix() + " for " + person.name() + " first");
        }
        Optional<CategoryId> id = activity.map(Category::id);
        PlaylistCopy wanted = copies.find(person.id(), id).filter(copy -> copy.service() == service)
                .orElseGet(() -> PlaylistCopy.wanted(person.id(), id, service));
        copies.save(wanted);
        return playlists.latest(person.id(), id, 1).stream().findFirst()
                .flatMap(newest -> copy(person, newest))
                .orElse("the next " + RecommendationList.nameFor(activity) + " playlist will be copied to "
                        + service.prefix());
    }

    public void stop(Person person, Optional<Category> activity) {
        copies.delete(person.id(), activity.map(Category::id));
    }

    /**
     * Writes the playlist as its activity's copy, when the person wants one; says how that went.
     */
    public Optional<String> copy(Person person, Playlist playlist) {
        return copies.find(person.id(), playlist.category())
                .filter(copy -> !person.provider().equals(Optional.of(copy.service())))
                .map(copy -> write(person, playlist, copy));
    }

    /**
     * After the activity's newest playlist was deleted, copies the one before it; with none left, takes the copy
     * away, still copying the next. Says how that went, to follow the word of the delete.
     */
    public Optional<String> followNewest(Person person, Optional<CategoryId> activity) {
        Optional<PlaylistCopy> copy = copies.find(person.id(), activity)
                .filter(found -> !person.provider().equals(Optional.of(found.service())));
        if (copy.isEmpty()) {
            return Optional.empty();
        }
        Optional<Playlist> newest = playlists.latest(person.id(), activity, 1).stream().findFirst();
        if (newest.isPresent()) {
            return Optional.of("; " + write(person, newest.get(), copy.get()));
        }
        return copy.get().playlistId().map(id -> remove(copy.get(), id));
    }

    private String remove(PlaylistCopy copy, String playlistId) {
        String where = copy.service().prefix();
        try {
            providers.get(copy.service()).deletePlaylist(copy.person(), playlistId);
            copies.save(PlaylistCopy.wanted(copy.person(), copy.category(), copy.service()));
            return ", and its copy from " + where;
        } catch (MusicProviderUnavailableException e) {
            return "; its copy is still in " + where + ": " + e.getMessage();
        }
    }

    /**
     * Gives the activity's copies its new name in each person's account; one that cannot be reached keeps the old.
     */
    public void renamed(Category activity) {
        String name = RecommendationList.nameFor(Optional.of(activity));
        for (PlaylistCopy copy : copies.forActivity(activity.id())) {
            copy.playlistId().ifPresent(id -> {
                try {
                    providers.get(copy.service()).renamePlaylist(copy.person(), id, name);
                } catch (MusicProviderUnavailableException e) {
                    LOG.log(System.Logger.Level.WARNING, "Could not rename the copy " + id, e);
                }
            });
        }
    }

    private String write(Person person, Playlist playlist, PlaylistCopy copy) {
        String where = copy.service().prefix();
        try {
            MusicProvider service = providers.get(copy.service());
            Set<String> found = new LinkedHashSet<>();
            for (Track track : playlist.tracks()) {
                (track.provider() == service.name() ? Optional.of(track.id())
                        : service.findTrack(track.artist(), track.title()).map(Track::id)).ifPresent(found::add);
            }
            if (found.isEmpty()) {
                return "not copied to " + where + ": none of its songs are there";
            }
            Optional<Playlist> sameName = copy.playlistId().isPresent() ? Optional.empty()
                    : writtenThereBefore(person, playlist, service.name());
            String id = service.replacePlaylist(person.id(),
                    copy.playlistId().or(() -> sameName.flatMap(Playlist::synced).map(SyncedPlaylist::playlistId)),
                    playlist.name(), playlist.description().isEmpty() ? "Picked for " + person.name()
                            : playlist.description(), List.copyOf(found));
            sameName.ifPresent(old -> playlists.save(old.outOfAccount()));
            copies.save(copy.writtenAs(id));
            return "copied to " + where + " as " + playlist.name() + ", " + found.size() + " of "
                    + playlist.tracks().size() + " songs";
        } catch (MusicProviderUnavailableException e) {
            return "not copied to " + where + ": " + e.getMessage();
        }
    }

    /**
     * The activity's playlist left on that service under the copy's own name, from before the person switched
     * services, which the copy replaces rather than sitting beside.
     */
    private Optional<Playlist> writtenThereBefore(Person person, Playlist playlist, Provider service) {
        return playlists.inAccount(person.id(), playlist.category()).stream()
                .filter(held -> held.inAccountOn(service))
                .filter(held -> held.slot().orElseThrow().nameInAccount().equals(playlist.name()))
                .findFirst();
    }
}
