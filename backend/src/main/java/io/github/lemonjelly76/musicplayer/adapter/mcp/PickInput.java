package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.application.RecommendationService.PickRequest;
import io.github.lemonjelly76.musicplayer.domain.recommendation.PickSource;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * One pick as the model writes it.
 */
record PickInput(
        @McpToolParam(description = "A ref from get_candidates or search_catalogue, e.g. tidal:251380836, or "
                + "'Artist — Title' for a wildcard") String albumRef,
        @McpToolParam(description = "One line on why this person will like it, tied to their taste") String reason,
        @McpToolParam(description = "pool (from get_candidates) or wildcard (your own idea)") String source) {

    PickRequest toRequest() {
        return new PickRequest(ToolInputs.text(albumRef), ToolInputs.text(reason),
                PickSource.parse(source == null ? "pool" : source));
    }
}
