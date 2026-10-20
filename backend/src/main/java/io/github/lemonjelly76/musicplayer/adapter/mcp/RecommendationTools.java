package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.CandidateView;
import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.ListView;
import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.PickView;
import io.github.lemonjelly76.musicplayer.application.CandidateService;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.RecommendationService;
import io.github.lemonjelly76.musicplayer.application.RecommendationService.Judged;
import io.github.lemonjelly76.musicplayer.application.RecommendationService.Saved;
import io.github.lemonjelly76.musicplayer.application.WishlistService;
import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.Verdict;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.attempt;
import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.json;

@Component
class RecommendationTools {

    static final int CANDIDATES_DEFAULT = 40;
    static final int CANDIDATES_MAX = 100;
    static final int SIMILAR_DEFAULT = 15;
    static final int LISTS_DEFAULT = 3;
    static final int LISTS_MAX = 20;
    private static final Pattern NAMED = Pattern.compile("^(.+?)\\s+[—–-]\\s+(.+)$");

    private final PersonService people;
    private final CategoryService categories;
    private final CandidateService candidates;
    private final RecommendationService recommendations;
    private final WishlistService wishlist;

    RecommendationTools(PersonService people, CategoryService categories, CandidateService candidates,
            RecommendationService recommendations, WishlistService wishlist) {
        this.people = people;
        this.categories = categories;
        this.candidates = candidates;
        this.recommendations = recommendations;
        this.wishlist = wishlist;
    }

    @McpTool(name = "get_candidates",
            description = "The server's candidate pool for a person: albums similar to their best-rated and "
                    + "most-played ones, from Last.fm and their streaming service, with what is in their library, "
                    + "recently recommended, already known or turned down for this category removed, and only what "
                    + "their service can play. Ranked by score, with the signals behind it (sources agreeing, "
                    + "seeds, seed rating, seed plays). Choose most picks from here.",
            annotations = @McpAnnotations(title = "Candidate pool", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = true))
    CallToolResult getCandidates(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Category name, e.g. Running", required = false) String category,
            @McpToolParam(description = "How many, 1 to 100, default 40", required = false) Integer limit) {
        return attempt(() -> json(candidates.candidates(people.find(person), categories.findOptional(category),
                        ToolInputs.limit(limit, CANDIDATES_DEFAULT, CANDIDATES_MAX)).stream()
                .map(CandidateView::from).toList()));
    }

    @McpTool(name = "get_similar",
            description = "Albums similar to one album ('Artist — Title') or to an artist ('Artist'), from Last.fm, "
                    + "unfiltered. Use it to explore beyond the pool.",
            annotations = @McpAnnotations(title = "Similar albums", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = true))
    CallToolResult getSimilar(
            @McpToolParam(description = "'Artist — Title' or just 'Artist'") String artistOrAlbum,
            @McpToolParam(description = "How many, default 15", required = false) Integer limit) {
        return attempt(() -> {
            String text = ToolInputs.text(artistOrAlbum).strip();
            Matcher named = NAMED.matcher(text);
            AlbumRef seed = named.matches() ? AlbumRef.of(named.group(1), named.group(2))
                    : AlbumRef.of(text, CandidateService.ANY_ALBUM);
            return json(candidates.similar(seed, ToolInputs.limit(limit, SIMILAR_DEFAULT, CANDIDATES_MAX)).stream()
                    .map(CandidateView::from).toList());
        });
    }

    @McpTool(name = "save_recommendations",
            description = "Saves a list of picks for a person, optionally for one category, and syncs it to their "
                    + "streaming service as a playlist named 'AI Picks' or 'AI Picks — <Category>', replacing the "
                    + "previous one. Every pick is checked: albums that cannot be found or played on their service, "
                    + "are already in their library, or were turned down are dropped and listed with the reason.",
            annotations = @McpAnnotations(title = "Save recommendations", destructiveHint = false, openWorldHint = true))
    CallToolResult saveRecommendations(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Category name; leave out for their general list", required = false)
            String category,
            @McpToolParam(description = "The picks, about 7 from the pool and up to 3 wildcards") List<PickInput> items) {
        return attempt(() -> {
            Person who = people.find(person);
            Saved saved = recommendations.save(who, categories.findOptional(category),
                    (items == null ? List.<PickInput>of() : items).stream().map(PickInput::toRequest).toList());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("list", ListView.from(saved.list(), categoryNames()));
            result.put("dropped", saved.dropped());
            result.put("sync", saved.sync());
            return json(result);
        });
    }

    @McpTool(name = "get_recommendations",
            description = "A person's latest lists, newest first, with every pick's id, reason and verdict.",
            annotations = @McpAnnotations(title = "Recommendation lists", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult getRecommendations(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Category name; leave out for every list", required = false) String category,
            @McpToolParam(description = "How many lists, default 3", required = false) Integer limit) {
        return attempt(() -> {
            Person who = people.find(person);
            Optional<Category> forCategory = categories.findOptional(category);
            int count = ToolInputs.limit(limit, LISTS_DEFAULT, LISTS_MAX);
            List<RecommendationList> lists = forCategory.isPresent()
                    ? recommendations.lists(who, forCategory, count)
                    : recommendations.history(who, count);
            Map<String, String> names = categoryNames();
            return json(lists.stream().map(list -> ListView.from(list, names)).toList());
        });
    }

    @McpTool(name = "record_feedback",
            description = "Records a person's verdict on a pick: liked (it also joins their library), not for me "
                    + "(not offered again for that category), or already know (never offered again).",
            annotations = @McpAnnotations(title = "Record feedback", idempotentHint = true, openWorldHint = false))
    CallToolResult recordFeedback(
            @McpToolParam(description = "The pick's id from get_recommendations") String recId,
            @McpToolParam(description = "liked, not for me, or already know") String verdict,
            @McpToolParam(description = "What they said about it", required = false) String comment) {
        return attempt(() -> {
            Judged judged = recommendations.feedback(ToolInputs.recommendationId(recId),
                    Verdict.parse(ToolInputs.text(verdict)), ToolInputs.text(comment));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("list", judged.list().name());
            result.put("pick", PickView.from(judged.pick()));
            result.put("addedToLibrary", judged.addedToLibrary());
            return json(result);
        });
    }

    @McpTool(name = "send_to_lidarr",
            description = "Adds an album to Lidarr as wanted, so it is downloaded and kept on the home server.",
            annotations = @McpAnnotations(title = "Send to Lidarr", destructiveHint = false, openWorldHint = true))
    CallToolResult sendToLidarr(
            @McpToolParam(description = "A ref such as mbid:<id> or tidal:251380836, or 'Artist — Title'")
            String albumRef) {
        return attempt(() -> json(Map.of("result", wishlist.want(ToolInputs.text(albumRef)))));
    }

    private Map<String, String> categoryNames() {
        return categories.all().stream().collect(Collectors.toMap(found -> found.id().toString(), Category::name));
    }
}
