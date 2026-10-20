package io.github.lemonjelly76.musicplayer.adapter.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Speaks just enough MCP over Streamable HTTP to call the running server the way Claude does.
 */
final class McpClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicInteger ids = new AtomicInteger();
    private final URI endpoint;
    private final String authorization;
    private String session;

    McpClient(URI endpoint, String authorization) {
        this.endpoint = endpoint;
        this.authorization = authorization;
    }

    McpClient initialize() throws IOException, InterruptedException {
        HttpResponse<String> response = post("""
                {"jsonrpc":"2.0","id":%d,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                 "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""".formatted(ids.incrementAndGet()));
        session = response.headers().firstValue("Mcp-Session-Id").orElseThrow();
        post("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}""");
        return this;
    }

    JsonNode request(String method, String params) throws IOException, InterruptedException {
        HttpResponse<String> response = post("""
                {"jsonrpc":"2.0","id":%d,"method":"%s","params":%s}""".formatted(ids.incrementAndGet(), method, params));
        return message(response.body()).path("result");
    }

    JsonNode callTool(String name, String arguments) throws IOException, InterruptedException {
        return request("tools/call", """
                {"name":"%s","arguments":%s}""".formatted(name, arguments));
    }

    static String text(JsonNode toolResult) {
        return toolResult.path("content").path(0).path("text").asString();
    }

    HttpResponse<String> post(String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        Optional.ofNullable(authorization).ifPresent(value -> request.header("Authorization", value));
        Optional.ofNullable(session).ifPresent(id -> request.header("Mcp-Session-Id", id));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * The server may answer as plain JSON or as one server-sent event.
     */
    private JsonNode message(String body) {
        String trimmed = body.strip();
        if (trimmed.startsWith("{")) {
            return json.readTree(trimmed);
        }
        return trimmed.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> json.readTree(line.substring(5).strip()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("No message in: " + body));
    }
}
