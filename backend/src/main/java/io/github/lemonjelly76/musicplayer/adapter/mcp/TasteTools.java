package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.AlbumView;
import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.CategoryView;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.LibraryService;
import io.github.lemonjelly76.musicplayer.application.TasteService;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.taste.TasteProfile;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.attempt;
import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.json;

@Component
class TasteTools {

    static final int MAX_RATINGS = 40;

    private final PersonService people;
    private final TasteService taste;
    private final CategoryService categories;
    private final LibraryService library;

    TasteTools(PersonService people, TasteService taste, CategoryService categories,
            LibraryService library) {
        this.people = people;
        this.taste = taste;
        this.categories = categories;
        this.library = library;
    }

    @McpTool(name = "get_taste_profile",
            description = "A person's taste profile: the summary you wrote last time, their 'never recommend' rules, "
                    + "their most recent ratings (love, ok or dislike, per activity) with what they said, and, given "
                    + "a category, its rules. Read this before choosing albums for them.",
            annotations = @McpAnnotations(title = "Taste profile", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult getTasteProfile(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Category name, e.g. Running", required = false) String category) {
        return attempt(() -> {
            Person who = people.find(person);
            Optional<Category> forCategory = categories.findOptional(category);
            TasteProfile profile = taste.profile(who.id());
            return json(new ProfileView(who.name(), profile.summary(), profile.neverRecommend(),
                    profile.updatedAt().orElse(null), forCategory.map(CategoryView::from).orElse(null),
                    recentRatings(who)));
        });
    }

    @McpTool(name = "update_taste_profile",
            description = "Rewrites a person's taste profile after a conversation or new feedback. Write the whole "
                    + "summary again, not just what changed. Rules replace the old ones only when given.",
            annotations = @McpAnnotations(title = "Update taste profile", idempotentHint = true, openWorldHint = false))
    CallToolResult updateTasteProfile(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "The full profile: genres, eras, moods, what they love and dislike, and why")
            String summary,
            @McpToolParam(description = "Hard rules, e.g. ['No Christmas albums', 'Nothing by Coldplay']",
                    required = false) List<String> neverRecommend) {
        return attempt(() -> {
            TasteProfile profile = taste.rewrite(people.find(person).id(), ToolInputs.text(summary),
                    Optional.ofNullable(neverRecommend));
            return json(Map.of("summary", profile.summary(), "neverRecommend", profile.neverRecommend()));
        });
    }

    @McpTool(name = "list_categories",
            description = "Lists the categories and activities everyone shares, such as Running or Focus, with the "
                    + "rules a list for each must follow.",
            annotations = @McpAnnotations(title = "Categories", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult listCategories() {
        return json(categories.all().stream().map(CategoryView::from).toList());
    }

    @McpTool(name = "upsert_category",
            description = "Adds a category or activity, or changes the rules of one with that name. Rules say what "
                    + "fits: energy, tempo, vocals, era, mood.",
            annotations = @McpAnnotations(title = "Add or change category", idempotentHint = true,
                    openWorldHint = false))
    CallToolResult upsertCategory(
            @McpToolParam(description = "e.g. Running, Driving, Focus, Rainy Sunday folk") String name,
            @McpToolParam(description = "e.g. 'High energy, steady 160-180 bpm feel, few slow songs'") String rules) {
        return attempt(() -> json(CategoryView.from(categories.upsert(ToolInputs.text(name), ToolInputs.text(rules)))));
    }

    private List<RatedView> recentRatings(Person person) {
        Map<CategoryId, String> names = categories.names();
        record Rated(LibraryAlbum entry, ActivityRating rating) {
        }
        return library.entries(person.id()).stream()
                .flatMap(entry -> entry.ratings().stream().map(rating -> new Rated(entry, rating)))
                .sorted(Comparator.comparing((Rated rated) -> rated.rating().ratedAt()).reversed())
                .limit(MAX_RATINGS)
                .map(rated -> new RatedView(AlbumView.from(rated.entry().album()),
                        Views.activityName(rated.rating().category(), names), rated.rating().rating().word(),
                        rated.rating().comment(), rated.rating().ratedAt()))
                .toList();
    }

    record ProfileView(String person, String summary, List<String> neverRecommend, Instant updatedAt,
                       CategoryView category, List<RatedView> recentRatings) {
    }

    record RatedView(AlbumView album, String activity, String rating, String comment, Instant at) {
    }
}
