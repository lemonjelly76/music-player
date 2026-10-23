package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.domain.playlist.Playlist;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The one-click "make me a playlist": single songs to play straight through, unlike album picks.
 */
@Component
class PlaylistPrompt {

    private static final String DESCRIPTION = "Makes a " + Playlist.SIZE + "-song playlist for a person, optionally "
            + "for one category, and writes it to their streaming service.";

    @McpPrompt(name = "playlist", title = "Playlist", description = DESCRIPTION)
    GetPromptResult playlist(
            @McpArg(name = "person", description = "Whose playlist: a name from list_people", required = true)
            String person,
            @McpArg(name = "category", description = "Optional category, e.g. Running", required = false)
            String category) {
        String scope = category == null || category.isBlank() ? "" : " for " + category.strip();
        String categoryArg = scope.isEmpty() ? "" : ", category " + category.strip();
        String text = """
                Make %1$s a %4$d-song playlist%2$s.

                1. Read get_taste_profile (person %1$s%3$s) for their taste for this activity alone and its rules \
                (each activity's taste is its own, so carry nothing over from the others), \
                get_library (person %1$s%3$s) for the albums they love there, get_songs (person %1$s%3$s) for \
                the songs they love or turned down there, and get_playlists (person %1$s%3$s) so the new one is \
                not a repeat of the last, noting how they rated its songs.
                2. Choose about 60 songs in the order they should play, with a shape that suits the activity. Mix \
                standout songs from albums they love with songs from artists like them they have not heard. \
                Single songs, not whole albums: at most two or three per artist. Follow their 'never recommend' \
                rules and the category's rules.
                3. Call save_playlist (person %1$s%3$s) with a one-line description and the songs as artist and \
                title. The first %4$d found are kept.
                4. Tell them what the playlist is like, and anything dropped and why.
                5. When they say what they think of a song, record it with rate_song for that activity."""
                .formatted(person, scope, categoryArg, Playlist.SIZE);
        return new GetPromptResult(DESCRIPTION, List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }
}
