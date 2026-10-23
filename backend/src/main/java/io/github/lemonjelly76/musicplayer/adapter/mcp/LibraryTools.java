package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.AlbumView;
import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.LibraryAlbumView;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.LibraryService.Added;
import io.github.lemonjelly76.musicplayer.application.LibraryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.domain.album.Rating;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryFilter;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.attempt;
import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.json;

@Component
class LibraryTools {

    static final int SEARCH_DEFAULT = 8;
    static final int SEARCH_MAX = 20;

    private final PersonService people;
    private final LibraryService library;
    private final CategoryService categories;
    private final MusicProviders providers;

    LibraryTools(PersonService people, LibraryService library, CategoryService categories, MusicProviders providers) {
        this.people = people;
        this.library = library;
        this.categories = categories;
        this.providers = providers;
    }

    /**
     * Mainstream, by its name or as General, is the activity that is no category.
     */
    private Optional<CategoryId> activity(String category) {
        return Category.meansMainstream(category)
                ? Optional.empty()
                : Optional.of(categories.find(category).id());
    }

    @McpTool(name = "search_catalogue",
            description = "Searches the streaming catalogue (the person's own service, else Tidal, else Spotify) for "
                    + "albums. Each result has a ref such as tidal:251380836 to pass to the other tools. For an "
                    + "artist, search their name and you get their albums.",
            annotations = @McpAnnotations(title = "Search catalogue", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = true))
    CallToolResult searchCatalogue(
            @McpToolParam(description = "What to look for, e.g. 'Talk Talk Spirit of Eden' or 'Pulp'") String query,
            @McpToolParam(description = "album (default) or artist", required = false) String type,
            @McpToolParam(description = "Whose service to search: name or id from list_people", required = false)
            String person,
            @McpToolParam(description = "How many results, 1 to 20, default 8", required = false) Integer limit) {
        return attempt(() -> {
            Optional<Person> who = ToolInputs.optional(person).map(people::find);
            MusicProvider catalogue = providers.catalogueFor(who).orElseThrow(() -> new IllegalArgumentException(
                    "No streaming service is set up; set TIDAL or SPOTIFY client ids, or use 'Artist — Title' refs"));
            int count = ToolInputs.limit(limit, SEARCH_DEFAULT, SEARCH_MAX);
            List<AlbumView> found = catalogue.searchAlbums(ToolInputs.text(query), count).stream()
                    .map(AlbumView::from).toList();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("service", catalogue.name().prefix());
            result.put("type", "album");
            result.put("albums", found);
            return json(result);
        });
    }

    @McpTool(name = "add_liked_album",
            description = "Saves an album a person likes to their library, rated for an activity (Mainstream when none "
                    + "is given; love when no rating is given), and to their streaming favourites when loved. An "
                    + "album already there keeps its notes and tags and takes the new rating.",
            annotations = @McpAnnotations(title = "Add liked album", destructiveHint = false, openWorldHint = true))
    CallToolResult addLikedAlbum(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "A ref from search_catalogue, e.g. spotify:4LH4d3cOWNNsVw41Gqt2kv, or "
                    + "'Artist — Title'") String albumRef,
            @McpToolParam(description = "love (default), ok or dislike", required = false) String rating,
            @McpToolParam(description = "Activity name, e.g. Running; leave out for Mainstream", required = false)
            String category,
            @McpToolParam(description = "Why they like it, in their words", required = false) String notes,
            @McpToolParam(description = "Tags such as genre, mood or era, e.g. ['britpop', '90s']", required = false)
            List<String> tags) {
        return attempt(() -> {
            Added added = library.add(people.find(person), albumRef, categories.findOptional(category),
                    ToolInputs.rating(rating).orElse(Rating.LOVE), ToolInputs.text(notes), ToolInputs.tags(tags));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("album", LibraryAlbumView.from(added.entry(), categories.names()));
            result.put("alreadyInLibrary", added.alreadyInLibrary());
            result.put("favourites", added.favouriteProblem());
            return json(result);
        });
    }

    @McpTool(name = "rate_album",
            description = "Records how much a person likes an album for an activity: love (it joins that activity's "
                    + "library and leads to similar albums), ok (kept, not used to find more) or dislike (never "
                    + "recommended for that activity). Without an activity it is the Mainstream rating, which, like "
                    + "any activity's, counts for Mainstream only. 'none' removes the rating. Use it whenever they "
                    + "say what they think of an album or a pick, with what they said as the comment.",
            annotations = @McpAnnotations(title = "Rate album", idempotentHint = true, openWorldHint = true))
    CallToolResult rateAlbum(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "A ref from get_recommendations, get_library or search_catalogue, or "
                    + "'Artist — Title'") String albumRef,
            @McpToolParam(description = "love, ok, dislike, or none to remove") String rating,
            @McpToolParam(description = "Activity name, e.g. Running; leave out for Mainstream", required = false)
            String category,
            @McpToolParam(description = "What they said about it", required = false) String comment) {
        return attempt(() -> {
            Optional<LibraryAlbum> rated = library.rate(people.find(person), albumRef,
                    categories.findOptional(category), ToolInputs.rating(rating), ToolInputs.text(comment));
            return json(rated.<Object>map(entry -> LibraryAlbumView.from(entry, categories.names()))
                    .orElse(Map.of("removed", albumRef)));
        });
    }

    @McpTool(name = "update_album",
            description = "Changes the notes or tags of an album in a person's library; use rate_album for ratings. "
                    + "Only the fields passed change; tags replace the old ones.",
            annotations = @McpAnnotations(title = "Update album", idempotentHint = true, openWorldHint = false))
    CallToolResult updateAlbum(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "The library album's id from get_library") String id,
            @McpToolParam(description = "New notes", required = false) String notes,
            @McpToolParam(description = "New tags", required = false) List<String> tags) {
        return attempt(() -> json(LibraryAlbumView.from(library.update(people.find(person).id(),
                ToolInputs.libraryAlbumId(id), Optional.ofNullable(notes), Optional.ofNullable(tags)),
                categories.names())));
    }

    @McpTool(name = "remove_album",
            description = "Removes an album and all its ratings from a person's library. Their streaming favourites "
                    + "are left alone. To say they dislike it, use rate_album instead.",
            annotations = @McpAnnotations(title = "Remove album", destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    CallToolResult removeAlbum(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "The library album's id from get_library") String id) {
        return attempt(() -> {
            library.remove(people.find(person).id(), ToolInputs.libraryAlbumId(id));
            return json(Map.of("removed", id));
        });
    }

    @McpTool(name = "get_library",
            description = "Lists a person's albums with their ratings per activity, notes, tags and date added. With "
                    + "an activity, only albums they love or rate ok for it, loved first; without, "
                    + "every album they have rated, disliked ones included.",
            annotations = @McpAnnotations(title = "Library", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult getLibrary(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Activity name, or Mainstream", required = false) String category,
            @McpToolParam(description = "Only albums with this tag", required = false) String tag,
            @McpToolParam(description = "Only albums from this decade, e.g. 1990", required = false) Integer decade,
            @McpToolParam(description = "Only albums by artists whose name contains this", required = false)
            String artist) {
        return attempt(() -> {
            PersonId who = people.find(person).id();
            LibraryFilter filter = new LibraryFilter(ToolInputs.optional(tag), Optional.ofNullable(decade),
                    ToolInputs.optional(artist));
            List<LibraryAlbum> entries = ToolInputs.optional(category).isEmpty()
                    ? library.all(who, filter)
                    : library.libraryFor(who, activity(category), filter);
            Map<CategoryId, String> names = categories.names();
            return json(entries.stream().map(entry -> LibraryAlbumView.from(entry, names)).toList());
        });
    }

    @McpTool(name = "import_favourites",
            description = "Copies the albums a person already saved on their streaming service into their library, "
                    + "tagged 'imported' and unrated, to seed their taste profile. Albums already there are skipped.",
            annotations = @McpAnnotations(title = "Import favourites", destructiveHint = false, idempotentHint = true,
                    openWorldHint = true))
    CallToolResult importFavourites(@McpToolParam(description = "Name or id from list_people") String person) {
        return attempt(() -> json(library.importFavourites(people.find(person))));
    }
}
