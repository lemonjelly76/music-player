package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.SongView;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.SongService;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.LibrarySong;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.attempt;
import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.json;

/**
 * Songs a person has rated, per activity as albums are, to steer playlists.
 */
@Component
class SongTools {

    private final PersonService people;
    private final CategoryService categories;
    private final SongService songs;

    SongTools(PersonService people, CategoryService categories, SongService songs) {
        this.people = people;
        this.categories = categories;
        this.songs = songs;
    }

    @McpTool(name = "rate_song",
            description = "Records how much a person likes a song for an activity: love, ok or dislike (left out of "
                    + "that activity's playlists). Without an activity it is the Mainstream rating, which, like any "
                    + "activity's, counts for Mainstream only. 'none' removes the rating. Use it whenever they say what "
                    + "they think of a song, in a playlist or anywhere else.",
            annotations = @McpAnnotations(title = "Rate song", idempotentHint = true, openWorldHint = true))
    CallToolResult rateSong(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "The artist, e.g. Air") String artist,
            @McpToolParam(description = "The song's title, e.g. Sexy Boy") String title,
            @McpToolParam(description = "love, ok, dislike, or none to remove") String rating,
            @McpToolParam(description = "Activity name, e.g. Running; leave out for Mainstream", required = false)
            String category) {
        return attempt(() -> {
            Optional<LibrarySong> rated = songs.rate(people.find(person), ToolInputs.text(artist),
                    ToolInputs.text(title), categories.findOptional(category), ToolInputs.rating(rating));
            return json(rated.<Object>map(song -> SongView.from(song, categories.names()))
                    .orElse(Map.of("removed", ToolInputs.text(artist) + " — " + ToolInputs.text(title))));
        });
    }

    @McpTool(name = "get_songs",
            description = "The songs a person has rated for an activity (Mainstream when none is given), only those: "
                    + "loved first, then ok, then those turned down. Read it before making a playlist: loved songs "
                    + "show what they want there, and turned-down ones are left out anyway.",
            annotations = @McpAnnotations(title = "Rated songs", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult getSongs(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Activity name; leave out for Mainstream", required = false) String category) {
        return attempt(() -> {
            Person who = people.find(person);
            Optional<CategoryId> activity = categories.findOptional(category).map(Category::id);
            Map<CategoryId, String> names = categories.names();
            return json(songs.songsFor(who.id(), activity).stream().map(song -> SongView.from(song, names)).toList());
        });
    }
}
