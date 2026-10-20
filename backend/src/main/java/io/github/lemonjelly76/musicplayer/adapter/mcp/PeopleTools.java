package io.github.lemonjelly76.musicplayer.adapter.mcp;

import io.github.lemonjelly76.musicplayer.adapter.mcp.Views.PersonView;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.attempt;
import static io.github.lemonjelly76.musicplayer.adapter.mcp.ToolResults.json;

@Component
class PeopleTools {

    private final PersonService people;
    private final MusicProviders providers;

    PeopleTools(PersonService people, MusicProviders providers) {
        this.people = people;
        this.providers = providers;
    }

    @McpTool(name = "list_people",
            description = "Lists the people who have music profiles, with their streaming service, whether they "
                    + "have connected it, whether their plays are counted and their Last.fm user.",
            annotations = @McpAnnotations(title = "People", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    CallToolResult listPeople() {
        return json(people.all().stream().map(this::view).toList());
    }

    @McpTool(name = "add_person",
            description = "Adds someone with their own library, taste profile and lists (at most 5 people). They "
                    + "connect their streaming account themselves from the app.",
            annotations = @McpAnnotations(title = "Add person", destructiveHint = false, openWorldHint = false))
    CallToolResult addPerson(
            @McpToolParam(description = "Their name, used to refer to them in every other tool") String name,
            @McpToolParam(description = "Their streaming service: tidal or spotify", required = false) String service) {
        return attempt(() -> json(view(people.add(ToolInputs.text(name),
                ToolInputs.optional(service).map(Provider::parse)))));
    }

    @McpTool(name = "set_person_service",
            description = "Changes someone's streaming service or Last.fm user (where their plays are read from "
                    + "when their service cannot say). Only the fields passed change; an empty lastFmUser removes it.",
            annotations = @McpAnnotations(title = "Set person's service", idempotentHint = true, openWorldHint = false))
    CallToolResult setPersonService(
            @McpToolParam(description = "Name or id from list_people") String person,
            @McpToolParam(description = "tidal or spotify", required = false) String service,
            @McpToolParam(description = "Their Last.fm user name", required = false) String lastFmUser) {
        return attempt(() -> {
            Person current = people.find(person);
            Person changed = current
                    .withProvider(service == null ? current.provider() : ToolInputs.optional(service).map(Provider::parse))
                    .withLastFmUser(lastFmUser == null ? current.lastFmUser() : ToolInputs.optional(lastFmUser));
            return json(view(people.update(changed)));
        });
    }

    private PersonView view(Person person) {
        return PersonView.from(person, providers.forPerson(person).map(provider -> connected(provider, person)));
    }

    private static boolean connected(MusicProvider provider, Person person) {
        return provider.connected(person.id());
    }
}
