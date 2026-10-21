package io.github.lemonjelly76.musicplayer.adapter.web;

import io.github.lemonjelly76.musicplayer.application.CategoryService;
import io.github.lemonjelly76.musicplayer.application.MusicProviders;
import io.github.lemonjelly76.musicplayer.application.PersonService;
import io.github.lemonjelly76.musicplayer.application.PlayService;
import io.github.lemonjelly76.musicplayer.application.PlaylistService;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@WebMvcTest(PeopleController.class)
@Import({SecurityConfiguration.class, ApiErrors.class})
@WithMockUser
class PeopleControllerTest {

    private static final Person CRAIG = Person.create("Craig", Optional.of(Provider.TIDAL));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PersonService people;
    @MockitoBean
    private CategoryService categories;
    @MockitoBean
    private PlayService plays;
    @MockitoBean
    private MusicProviders providers;
    @MockitoBean
    private PlaylistService playlists;

    @BeforeEach
    void setUp() {
        given(people.get(CRAIG.id())).willReturn(CRAIG);
        given(people.update(any())).willAnswer(call -> call.getArgument(0));
    }

    @Test
    void saysWhichServicesTheServerIsSetUpFor() {
        given(providers.find(Provider.TIDAL)).willReturn(Optional.of(mock(MusicProvider.class)));
        given(providers.find(Provider.SPOTIFY)).willReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/services"))
                .hasStatusOk()
                .bodyJson().isEqualTo("""
                        [{"service":"tidal","configured":true},{"service":"spotify","configured":false}]""");
    }

    @Test
    void renamesAnActivityAndItsPlaylists() {
        Category focus = Category.create("Focus", "Instrumental");
        given(categories.rename("c-1", "Focus")).willReturn(focus);

        assertThat(mvc.put().uri("/api/categories/{id}", "c-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Focus"}"""))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"name":"Focus","rules":"Instrumental"}""");
        verify(playlists).renameFor(focus);
    }

    @Test
    void removesAnActivityForEveryone() {
        assertThat(mvc.delete().uri("/api/categories/{id}", "c-1")).hasStatus(HttpStatus.NO_CONTENT);

        verify(categories).remove("c-1");
    }

    @Test
    void changesAPersonsService() {
        MusicProvider spotify = mock(MusicProvider.class);
        given(providers.forPerson(any())).willReturn(Optional.of(spotify));

        assertThat(mvc.put().uri("/api/people/{id}/service", CRAIG.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"service":"spotify"}"""))
                .hasStatusOk()
                .bodyJson().extractingPath("$.service").isEqualTo("spotify");
        verify(people).update(CRAIG.withProvider(Optional.of(Provider.SPOTIFY)));
    }

    @Test
    void anUnknownServiceIsABadRequest() {
        assertThat(mvc.put().uri("/api/people/{id}/service", CRAIG.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"service":"deezer"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
