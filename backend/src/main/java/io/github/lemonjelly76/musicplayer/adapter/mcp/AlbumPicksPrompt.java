package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The one-click "give me this week's albums" (ADR-007): the steps the model takes, with the server doing the
 * mechanical work either side of its choices.
 */
@Component
class AlbumPicksPrompt {

    private static final String DESCRIPTION = "Picks about 10 albums for a person to try, optionally for one "
            + "category, and explains each.";

    @McpPrompt(name = "album_picks", title = "Album picks", description = DESCRIPTION)
    GetPromptResult albumPicks(
            @McpArg(name = "person", description = "Whose picks: a name from list_people", required = true)
            String person,
            @McpArg(name = "category", description = "Optional category, e.g. Running", required = false)
            String category) {
        String scope = category == null || category.isBlank() ? "" : " for the category " + category.strip();
        String categoryArg = scope.isEmpty() ? "" : ", category " + category.strip();
        String text = """
                Pick about 10 albums %1$s will love%2$s.

                1. Read get_taste_profile (person %1$s%3$s) for their profile, rules, recent ratings and the \
                category's rules, and get_play_stats for what they keep replaying (if it returns nothing, they \
                have turned counting off; leave it at that).
                2. Read get_candidates (person %1$s%3$s): the server's filtered, ranked pool.
                3. Choose about 7 from the pool and up to 3 wildcards of your own: deep cuts and older or less \
                obvious albums the similarity data misses. Whole albums, not compilations, unless they ask. Follow \
                their 'never recommend' rules and the category's rules.
                4. Call save_recommendations (person %1$s%3$s) with a one-line reason for each pick, tied to \
                something specific in their taste. Use the pool's refs; give wildcards as 'Artist — Title'.
                5. Tell them the list, and anything dropped and why. Replace a dropped pick if you can. The                 albums show in their app to open and rate; for songs to play straight through, the playlist prompt                 makes a playlist.
                6. When they say what they think of a pick, record it with rate_album (love, ok or dislike) for \
                that activity. If what they told you changes your picture of their taste, rewrite it with \
                update_taste_profile."""
                .formatted(person, scope, categoryArg);
        return new GetPromptResult(DESCRIPTION, List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }
}
