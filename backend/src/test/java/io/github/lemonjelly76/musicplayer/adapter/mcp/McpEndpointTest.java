package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.IntegrationTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestConfiguration.class)
@TestPropertySource(properties = "app.security.mcp-token=0123456789abcdef0123456789abcdef")
class McpEndpointTest {

    private static final String BASIC = "Basic " + Base64.getEncoder()
            .encodeToString("tester:test-password".getBytes(StandardCharsets.UTF_8));
    private static final String BEARER = "Bearer 0123456789abcdef0123456789abcdef";

    @LocalServerPort
    private int port;

    private URI endpoint() {
        return URI.create("http://localhost:" + port + "/mcp");
    }

    @Test
    void offersEveryToolTheSpecAsksFor() throws Exception {
        JsonNode tools = new McpClient(endpoint(), BASIC).initialize().request("tools/list", "{}").path("tools");

        List<String> names = new ArrayList<>();
        tools.forEach(tool -> names.add(tool.path("name").asString()));
        assertThat(names).contains("list_people", "add_person", "search_catalogue", "add_liked_album",
                "update_album", "remove_album", "get_library", "get_taste_profile", "update_taste_profile",
                "list_categories", "upsert_category", "get_play_stats", "set_play_tracking", "get_candidates",
                "get_similar", "save_recommendations", "rate_album", "send_to_lidarr", "import_favourites",
                "get_recommendations", "save_playlist", "get_playlists", "sync_playlist", "rate_song", "get_songs");
    }

    @Test
    void offersTheAlbumPicksPrompt() throws Exception {
        JsonNode prompt = new McpClient(endpoint(), BASIC).initialize()
                .request("prompts/get", """
                        {"name":"album_picks","arguments":{"person":"Craig","category":"Running"}}""");

        assertThat(prompt.path("messages").path(0).path("content").path("text").asString())
                .contains("get_candidates (person Craig, category Running)", "save_recommendations");
    }

    @Test
    void offersThePlaylistPrompt() throws Exception {
        JsonNode prompt = new McpClient(endpoint(), BASIC).initialize()
                .request("prompts/get", """
                        {"name":"playlist","arguments":{"person":"Craig","category":"Running"}}""");

        assertThat(prompt.path("messages").path(0).path("content").path("text").asString())
                .contains("50", "save_playlist (person Craig, category Running)");
    }

    @Test
    void refusesAPlaylistForSomeoneWithNoServiceToFindSongsOn() throws Exception {
        McpClient claude = new McpClient(endpoint(), BEARER).initialize();
        claude.callTool("add_person", """
                {"name":"Sam"}""");

        JsonNode saved = claude.callTool("save_playlist", """
                {"person":"Sam","description":"Warm up","tracks":[{"artist":"Air","title":"Sexy Boy"}]}""");

        assertThat(saved.path("isError").asBoolean()).isTrue();
        assertThat(McpClient.text(saved)).contains("Sam has no streaming service");
    }

    @Test
    void managesALibraryAndRefusesPicksThatCannotBeChecked() throws Exception {
        McpClient claude = new McpClient(endpoint(), BEARER).initialize();

        claude.callTool("add_person", """
                {"name":"Robin"}""");
        claude.callTool("upsert_category", """
                {"name":"Focus","rules":"Mostly instrumental"}""");
        JsonNode added = claude.callTool("add_liked_album", """
                {"person":"robin","albumRef":"Boards of Canada — Music Has the Right to Children","rating":"love",
                 "tags":["Electronic"]}""");
        JsonNode library = claude.callTool("get_library", """
                {"person":"Robin","tag":"electronic"}""");
        JsonNode saved = claude.callTool("save_recommendations", """
                {"person":"Robin","category":"Focus",
                 "items":[{"albumRef":"Made Up — Never Released","reason":"Calm","source":"wildcard"}]}""");

        assertThat(added.path("isError").asBoolean()).isFalse();
        assertThat(McpClient.text(library)).contains("Music Has the Right to Children", "\"rating\":\"love\"");
        assertThat(saved.path("isError").asBoolean()).isTrue();
        assertThat(McpClient.text(saved)).contains("No pick could be kept", "Made Up — Never Released");
    }

    @Test
    void keepsEachActivitysTasteApartWithMainstreamAsOneOfThem() throws Exception {
        McpClient claude = new McpClient(endpoint(), BEARER).initialize();
        claude.callTool("add_person", """
                {"name":"Sam"}""");
        claude.callTool("upsert_category", """
                {"name":"Dinner","rules":"Relaxed, with vocals"}""");
        claude.callTool("add_liked_album", """
                {"person":"Sam","albumRef":"Boards of Canada — Music Has the Right to Children","category":"Dinner"}""");
        claude.callTool("update_taste_profile", """
                {"person":"Sam","category":"Dinner","summary":"Soft vocals","neverRecommend":["No metal"]}""");
        claude.callTool("update_taste_profile", """
                {"person":"Sam","summary":"Big pop hooks"}""");

        claude.callTool("rate_album", """
                {"person":"Sam","albumRef":"Boards of Canada — Music Has the Right to Children","rating":"ok",
                 "category":"Dinner"}""");
        String dinner = McpClient.text(claude.callTool("get_taste_profile", """
                {"person":"Sam","category":"Dinner"}"""));
        String mainstream = McpClient.text(claude.callTool("get_taste_profile", """
                {"person":"Sam","category":"Mainstream"}"""));

        assertThat(dinner).contains("\"activity\":\"Dinner\"", "\"ratingsSinceSummary\":1", "Relaxed, with vocals",
                "Soft vocals", "No metal",
                "Music Has the Right to Children").doesNotContain("Big pop hooks");
        assertThat(mainstream).contains("\"activity\":\"Mainstream\"", "\"ratingsSinceSummary\":0", "Big pop hooks")
                .doesNotContain("Soft vocals", "No metal", "Music Has the Right to Children");
    }

    @Test
    void reportsAnUnknownPersonAsAToolError() throws Exception {
        JsonNode result = new McpClient(endpoint(), BASIC).initialize().callTool("get_library", """
                {"person":"Nobody"}""");

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(McpClient.text(result)).contains("No person called 'Nobody'");
    }

    @Test
    void needsTheLoginOrTheToken() throws Exception {
        assertThat(new McpClient(endpoint(), null).post("{}").statusCode()).isEqualTo(401);
        assertThat(new McpClient(endpoint(), "Bearer wrong-token-wrong-token-wrong-token").post("{}").statusCode())
                .isEqualTo(401);
    }

    @Test
    void theTokenOpensNothingButMcp() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/people"))
                        .header("Authorization", BEARER).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }
}
