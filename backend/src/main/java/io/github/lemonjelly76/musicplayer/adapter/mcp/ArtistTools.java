package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.RatingView;
import io.github.lemonjelly76.musicplayer.application.ArtistService;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtist;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.attempt;
import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.json;

@Component
class ArtistTools {

    private final PersonService people;
    private final CategoryService categories;
    private final ArtistService artists;

    ArtistTools(PersonService people, CategoryService categories, ArtistService artists) {
        this.people = people;
        this.categories = categories;
        this.artists = artists;
    }

    @McpTool(name = "rate_artist",
            description = "Records how much a person likes an artist as a whole for an activity: love (their albums "
                    + "and artists like them lead the picks for it), ok, or dislike (nothing of theirs is picked or "
                    + "put in a playlist for that activity, even made with others). Without an activity it is the "
                    + "Mainstream rating, which counts for Mainstream only. 'none' removes the rating. Use it when "
                    + "they say what they think of an artist rather than one album or song.",
            annotations = @McpAnnotations(title = "Rate artist", idempotentHint = true, openWorldHint = false))
    CallToolResult rateArtist(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "The artist, e.g. Air") String artist,
            @McpToolParam(description = "love, ok, dislike, or none to remove") String rating,
            @McpToolParam(description = "Activity name, e.g. Running; leave out for Mainstream", required = false)
            String category,
            @McpToolParam(description = "What they said about them", required = false) String comment) {
        return attempt(() -> {
            Optional<LibraryArtist> rated = artists.rate(people.find(person), artist,
                    categories.findOptional(category), ToolInputs.rating(rating), ToolInputs.text(comment));
            return json(rated.<Object>map(found -> ArtistView.from(found, categories))
                    .orElse(Map.of("removed", ToolInputs.text(artist))));
        });
    }

    record ArtistView(String artist, List<RatingView> ratings) {

        static ArtistView from(LibraryArtist artist, CategoryService categories) {
            return new ArtistView(artist.name(), artist.ratings().stream()
                    .map(rating -> RatingView.from(rating, categories.names())).toList());
        }
    }
}
