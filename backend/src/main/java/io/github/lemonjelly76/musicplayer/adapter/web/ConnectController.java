package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.adapter.oauth.ProviderAuthorization;
import io.github.lemonjelly76.musicplayer.adapter.web.Responses.ConnectResponse;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Optional;

/**
 * Connects a person's own streaming account (ADR-008). The app asks for the sign-in link and opens it in the browser;
 * the service sends the browser back to the callback, which needs no login because the state it carries was issued
 * for that person and expires in minutes.
 */
@RestController
class ConnectController {

    private final PersonService people;
    private final List<ProviderAuthorization> authorizations;

    ConnectController(PersonService people, List<ProviderAuthorization> authorizations) {
        this.people = people;
        this.authorizations = authorizations;
    }

    /**
     * Signs the person in to their own service, or to the one named, as when they copy playlists to a second.
     */
    @GetMapping("/api/people/{id}/connect")
    ConnectResponse connect(@PathVariable String id, @RequestParam(required = false) String service) {
        Person person = people.get(PersonId.of(id));
        Provider chosen = named(service).or(person::provider).orElseThrow(() -> new IllegalArgumentException(
                person.name() + " has no streaming service set; choose tidal or spotify first"));
        return new ConnectResponse(chosen.prefix(), authorization(chosen).connectUrl(person.id()).toString());
    }

    @DeleteMapping("/api/people/{id}/connect")
    ResponseEntity<Void> disconnect(@PathVariable String id, @RequestParam(required = false) String service) {
        Person person = people.get(PersonId.of(id));
        named(service).or(person::provider).ifPresent(chosen -> authorization(chosen).disconnect(person.id()));
        return ResponseEntity.noContent().build();
    }

    private static Optional<Provider> named(String service) {
        return Optional.ofNullable(service).filter(text -> !text.isBlank()).map(Provider::parse);
    }

    @GetMapping(value = "/api/connect/{service}/callback", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> callback(@PathVariable String service, @RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
        Provider provider = Provider.parse(service);
        if (error != null || code == null) {
            return ResponseEntity.badRequest().body(page("Not connected",
                    provider.prefix() + " did not connect: " + (error == null ? "no code was returned" : error)));
        }
        try {
            Person person = people.get(authorization(provider).complete(code, state));
            return ResponseEntity.ok(page("Connected", person.name() + "'s " + provider.prefix()
                    + " account is connected. You can close this page and go back to the app."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(page("Not connected", e.getMessage()));
        }
    }

    private ProviderAuthorization authorization(Provider provider) {
        return authorizations.stream().filter(found -> found.provider() == provider).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The app is not set up for " + provider.prefix()));
    }

    private static String page(String title, String message) {
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1"><title>%1$s</title></head>
                <body style="font-family: sans-serif; margin: 2rem"><h1>%1$s</h1><p>%2$s</p></body></html>"""
                .formatted(HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(message));
    }
}
