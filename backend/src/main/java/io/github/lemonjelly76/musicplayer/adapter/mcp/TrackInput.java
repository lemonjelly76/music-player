package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.application.PlaylistService.TrackRequest;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * One song as the model names it.
 */
record TrackInput(
        @McpToolParam(description = "The artist, e.g. Air") String artist,
        @McpToolParam(description = "The song's title, e.g. Sexy Boy") String title) {

    TrackRequest toRequest() {
        return new TrackRequest(ToolInputs.text(artist), ToolInputs.text(title));
    }
}
