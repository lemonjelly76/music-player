package io.github.lemonjelly76.musicplayer.adapter.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.lemonjelly76.musicplayer.application.NotFoundException;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProviderUnavailableException;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Supplier;

/**
 * Turns tool outcomes into MCP results: compact JSON for data, and short error texts the model can act on.
 */
final class ToolResults {

    private static final JsonMapper JSON = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_EMPTY))
            .build();

    private ToolResults() {
    }

    static CallToolResult json(Object value) {
        return text(JSON.writeValueAsString(value));
    }

    static CallToolResult text(String text) {
        return CallToolResult.builder().addTextContent(text).isError(false).build();
    }

    static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }

    /**
     * Runs a tool, reporting unknown names and ids, invalid input and an unreachable service as tool errors rather
     * than failures of the call.
     */
    static CallToolResult attempt(Supplier<CallToolResult> tool) {
        try {
            return tool.get();
        } catch (NotFoundException | IllegalArgumentException | MusicProviderUnavailableException e) {
            return error(e.getMessage());
        }
    }
}
