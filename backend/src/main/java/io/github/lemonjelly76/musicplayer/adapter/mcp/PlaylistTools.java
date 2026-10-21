package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.PlaylistView;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlaylistService;
import io.github.lemonjelly76.musicplayer.application.PlaylistService.Saved;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.attempt;
import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.json;

/**
 * Song playlists: the model names the songs, the server finds them and writes the playlist to the person's account.
 */
@Component
class PlaylistTools {

    static final int PLAYLISTS_DEFAULT = 1;
    static final int PLAYLISTS_MAX = 10;

    private final PersonService people;
    private final CategoryService categories;
    private final PlaylistService playlists;

    PlaylistTools(PersonService people, CategoryService categories, PlaylistService playlists) {
        this.people = people;
        this.categories = categories;
        this.playlists = playlists;
    }

    @McpTool(name = "save_playlist",
            description = "Saves a playlist of single songs (not albums) for a person, optionally for one category, "
                    + "and writes it to their streaming service as 'AI Picks' or 'AI Picks — <Category>', replacing "
                    + "the previous one. It keeps the first " + Playlist.SIZE + " songs found, so give about 60 in "
                    + "play order. Songs their service does not have, repeats and songs from albums they turned down "
                    + "for the category are dropped and listed with the reason.",
            annotations = @McpAnnotations(title = "Save playlist", destructiveHint = false, openWorldHint = true))
    CallToolResult savePlaylist(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Category name; leave out for their general playlist", required = false)
            String category,
            @McpToolParam(description = "One line on the playlist's mood or arc, shown with it") String description,
            @McpToolParam(description = "The songs in play order, about 60") List<TrackInput> tracks) {
        return attempt(() -> {
            Saved saved = playlists.save(people.find(person), categories.findOptional(category),
                    ToolInputs.text(description),
                    (tracks == null ? List.<TrackInput>of() : tracks).stream().map(TrackInput::toRequest).toList());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("playlist", saved.playlist().name());
            result.put("kept", saved.playlist().tracks().size());
            result.put("dropped", saved.dropped());
            result.put("sync", saved.sync());
            return json(result);
        });
    }

    @McpTool(name = "get_playlists",
            description = "A person's latest playlists for a category (or their general ones), newest first, with "
                    + "every song. Read it before a new playlist so songs are not all repeated.",
            annotations = @McpAnnotations(title = "Playlists", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult getPlaylists(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Category name; leave out for their general playlists", required = false)
            String category,
            @McpToolParam(description = "How many playlists, default 1", required = false) Integer limit) {
        return attempt(() -> {
            Person who = people.find(person);
            Optional<Category> forCategory = categories.findOptional(category);
            return json(playlists.latest(who, forCategory.map(Category::id),
                            ToolInputs.limit(limit, PLAYLISTS_DEFAULT, PLAYLISTS_MAX)).stream()
                    .map(playlist -> PlaylistView.from(playlist, forCategory.map(Category::name).orElse("General")))
                    .toList());
        });
    }

    @McpTool(name = "sync_playlist",
            description = "Writes a person's latest playlist for a category to their streaming service again, for "
                    + "one saved while the service was unavailable or before they connected it.",
            annotations = @McpAnnotations(title = "Sync playlist", idempotentHint = true, openWorldHint = true))
    CallToolResult syncPlaylist(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Category name; leave out for their general playlist", required = false)
            String category) {
        return attempt(() -> {
            Saved saved = playlists.resync(people.find(person), categories.findOptional(category));
            return json(Map.of("playlist", saved.playlist().name(), "sync", saved.sync()));
        });
    }
}
