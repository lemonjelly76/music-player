package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.PlaysView;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlayService;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.plays.AlbumPlays;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.attempt;
import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.json;

@Component
class PlayTools {

    static final int DEFAULT_DAYS = 90;
    static final int MAX_DAYS = 3650;
    static final int TOP = 30;

    private final PersonService people;
    private final CategoryService categories;
    private final PlayService plays;

    PlayTools(PersonService people, CategoryService categories, PlayService plays) {
        this.people = people;
        this.categories = categories;
        this.plays = plays;
    }

    @McpTool(name = "get_play_stats",
            description = "A person's most-played albums, counted in tracks played, over the last days; with a "
                    + "category, only plays from that category's lists. Returns nothing when they have turned "
                    + "counting off there: do not ask them to turn it on.",
            annotations = @McpAnnotations(title = "Play stats", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult getPlayStats(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Category name", required = false) String category,
            @McpToolParam(description = "How many days back, default 90", required = false) Integer days) {
        return attempt(() -> {
            Person who = people.find(person);
            Optional<Category> forCategory = categories.findOptional(category);
            int period = ToolInputs.limit(days, DEFAULT_DAYS, MAX_DAYS);
            Optional<List<AlbumPlays>> stats = plays.stats(who, forCategory, period);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tracking", stats.isPresent());
            result.put("days", period);
            stats.ifPresent(totals -> result.put("albums", totals.stream().limit(TOP).map(PlaysView::from).toList()));
            return json(result);
        });
    }

    @McpTool(name = "set_play_tracking",
            description = "Turns counting of a person's plays on or off, for them or for one category. A category "
                    + "can also be set back to 'default' to follow the person's setting. Only do this when they "
                    + "ask. Counts already kept stay unless clearExisting is true.",
            annotations = @McpAnnotations(title = "Set play tracking", idempotentHint = true, openWorldHint = false))
    CallToolResult setPlayTracking(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Category name; leave out for the person as a whole", required = false)
            String category,
            @McpToolParam(description = "on, off, or default (a category only)") String enabled,
            @McpToolParam(description = "Also delete the counts already kept there", required = false)
            Boolean clearExisting) {
        return attempt(() -> {
            Person who = people.find(person);
            Optional<Category> forCategory = categories.findOptional(category);
            plays.setTracking(who, forCategory, setting(enabled));
            if (Boolean.TRUE.equals(clearExisting)) {
                plays.clear(who, forCategory);
            }
            Person updated = people.get(who.id());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("person", updated.name());
            forCategory.ifPresent(found -> result.put("category", found.name()));
            result.put("tracking", plays.tracking(updated).enabledFor(forCategory.map(Category::id)));
            result.put("cleared", Boolean.TRUE.equals(clearExisting));
            return json(result);
        });
    }

    private static Optional<Boolean> setting(String text) {
        return switch (ToolInputs.text(text).strip().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes" -> Optional.of(true);
            case "off", "false", "no" -> Optional.of(false);
            case "default" -> Optional.empty();
            default -> throw new IllegalArgumentException("enabled must be on, off or default, got '" + text + "'");
        };
    }
}
