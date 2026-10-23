package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.AlbumView;
import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.CategoryView;
import io.github.lemonjelly76.musicplayer.application.ArtistService;
import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.LibraryService;
import io.github.lemonjelly76.musicplayer.application.SongService;
import io.github.lemonjelly76.musicplayer.application.TasteService;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.library.ActivityRating;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryAlbum;
import io.github.lemonjelly76.musicplayer.domain.library.LibraryArtist;
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
import java.util.stream.Stream;

import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.attempt;
import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.json;

@Component
class TasteTools {

    static final int MAX_RATINGS = 40;
    static final String CARRIED_OVER = "Copied from the one profile every activity used to share. Rewrite it with "
            + "update_taste_profile from this activity's own ratings, leaving out what belongs to other activities.";

    private final PersonService people;
    private final TasteService taste;
    private final CategoryService categories;
    private final LibraryService library;
    private final SongService songs;
    private final ArtistService artists;

    TasteTools(PersonService people, TasteService taste, CategoryService categories, LibraryService library,
            SongService songs, ArtistService artists) {
        this.people = people;
        this.taste = taste;
        this.categories = categories;
        this.library = library;
        this.songs = songs;
        this.artists = artists;
    }

    @McpTool(name = "get_taste_profile",
            description = "A person's taste for one activity (Mainstream when none is given): the summary you wrote "
                    + "for it last time, its 'never recommend' rules, their most recent ratings for it (love, ok or "
                    + "dislike) and the artists they rated for it, with what they said, how many albums, songs and "
                    + "artists they have rated for it since the "
                    + "summary was written, and the activity's rules. Each activity's taste is its own: "
                    + "never carry what they like for one over to another. Read this before choosing for them.",
            annotations = @McpAnnotations(title = "Taste profile", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult getTasteProfile(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "Activity name, e.g. Running; leave out for Mainstream", required = false)
            String category) {
        return attempt(() -> {
            Person who = people.find(person);
            Optional<Category> activity = categories.findOptional(category);
            Optional<CategoryId> id = activity.map(Category::id);
            TasteProfile profile = taste.profile(who.id(), id);
            return json(new ProfileView(who.name(), activity.map(Category::name).orElse(Category.MAINSTREAM),
                    activity.map(Category::rules).orElse(null), profile.summary(),
                    profile.carriedOver() ? CARRIED_OVER : null, profile.neverRecommend(),
                    profile.updatedAt().orElse(null), ratingsSince(who, id, profile.updatedAt()),
                    recentRatings(who, id), artists.artistsFor(who.id(), id).stream()
                            .map(artist -> RatedArtistView.from(artist, id)).toList()));
        });
    }

    @McpTool(name = "update_taste_profile",
            description = "Rewrites a person's taste for one activity (Mainstream when none is given) after a "
                    + "conversation or new ratings for it. Write the whole summary again, from that activity's "
                    + "ratings and what they said about it only. Rules replace the old ones only when given.",
            annotations = @McpAnnotations(title = "Update taste profile", idempotentHint = true, openWorldHint = false))
    CallToolResult updateTasteProfile(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "The full profile: genres, eras, moods, what they love and dislike, and why")
            String summary,
            @McpToolParam(description = "Hard rules, e.g. ['No Christmas albums', 'Nothing by Coldplay']",
                    required = false) List<String> neverRecommend,
            @McpToolParam(description = "Activity name, e.g. Running; leave out for Mainstream", required = false)
            String category) {
        return attempt(() -> {
            Optional<Category> activity = categories.findOptional(category);
            TasteProfile profile = taste.rewrite(people.find(person).id(), activity.map(Category::id),
                    ToolInputs.text(summary), Optional.ofNullable(neverRecommend));
            return json(Map.of("activity", activity.map(Category::name).orElse(Category.MAINSTREAM),
                    "summary", profile.summary(), "neverRecommend", profile.neverRecommend()));
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

    /**
     * The activity's album, song and artist ratings given after the summary was written, or all of them before there
     * was one.
     */
    private long ratingsSince(Person person, Optional<CategoryId> activity, Optional<Instant> since) {
        return Stream.of(
                        library.entries(person.id()).stream().flatMap(entry -> entry.own(activity).stream()),
                        songs.songsFor(person.id(), activity).stream().flatMap(song -> song.own(activity).stream()),
                        artists.artistsFor(person.id(), activity).stream()
                                .flatMap(artist -> artist.own(activity).stream()))
                .flatMap(ratings -> ratings)
                .filter(rating -> since.map(rating.ratedAt()::isAfter).orElse(true))
                .count();
    }

    private List<RatedView> recentRatings(Person person, Optional<CategoryId> activity) {
        record Rated(LibraryAlbum entry, ActivityRating rating) {
        }
        return library.entries(person.id()).stream()
                .flatMap(entry -> entry.own(activity).stream().map(rating -> new Rated(entry, rating)))
                .sorted(Comparator.comparing((Rated rated) -> rated.rating().ratedAt()).reversed())
                .limit(MAX_RATINGS)
                .map(rated -> new RatedView(AlbumView.from(rated.entry().album()), rated.rating().rating().word(),
                        rated.rating().comment(), rated.rating().ratedAt()))
                .toList();
    }

    record ProfileView(String person, String activity, String rules, String summary, String note,
                       List<String> neverRecommend, Instant updatedAt, long ratingsSinceSummary,
                       List<RatedView> recentRatings, List<RatedArtistView> artists) {
    }

    record RatedArtistView(String name, String rating, String comment) {

        static RatedArtistView from(LibraryArtist artist, Optional<CategoryId> activity) {
            ActivityRating rating = artist.own(activity).orElseThrow();
            return new RatedArtistView(artist.name(), rating.rating().word(), rating.comment());
        }
    }

    record RatedView(AlbumView album, String rating, String comment, Instant at) {
    }
}
