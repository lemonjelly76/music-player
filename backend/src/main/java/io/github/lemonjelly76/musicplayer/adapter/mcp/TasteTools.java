package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.AlbumView;
import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.CategoryView;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.RecommendationService;
import io.github.lemonjelly76.musicplayer.application.TasteService;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Recommendation;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.taste.TasteProfile;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.attempt;
import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.json;

@Component
class TasteTools {

    static final int FEEDBACK_LISTS = 20;
    static final int MAX_FEEDBACK = 40;

    private final PersonService people;
    private final TasteService taste;
    private final CategoryService categories;
    private final RecommendationService recommendations;

    TasteTools(PersonService people, TasteService taste, CategoryService categories,
            RecommendationService recommendations) {
        this.people = people;
        this.taste = taste;
        this.categories = categories;
        this.recommendations = recommendations;
    }

    @McpTool(name = "get_taste_profile",
            description = "A person's taste profile: the summary you wrote last time, their 'never recommend' rules, "
                    + "their recent verdicts on picks with comments, and, given a category, its rules. Read this "
                    + "before choosing albums for them.",
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
                    recentFeedback(who)));
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

    private List<FeedbackView> recentFeedback(Person person) {
        Map<CategoryId, String> names = categories.all().stream()
                .collect(Collectors.toMap(Category::id, Category::name));
        List<FeedbackView> feedback = new ArrayList<>();
        for (RecommendationList list : recommendations.history(person, FEEDBACK_LISTS)) {
            for (Recommendation pick : list.picks()) {
                pick.feedback().ifPresent(given -> feedback.add(new FeedbackView(AlbumView.from(pick.album()),
                        list.category().map(names::get).orElse(null), given.verdict().name(), given.comment(),
                        given.at())));
            }
        }
        return feedback.stream().limit(MAX_FEEDBACK).toList();
    }

    record ProfileView(String person, String summary, List<String> neverRecommend, Instant updatedAt,
                       CategoryView category, List<FeedbackView> recentFeedback) {
    }

    record FeedbackView(AlbumView album, String category, String verdict, String comment, Instant at) {
    }
}
