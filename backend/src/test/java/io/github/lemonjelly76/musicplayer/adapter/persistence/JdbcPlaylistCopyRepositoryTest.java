package io.github.lemonjelly76.musicplayer.adapter.persistence;

import io.github.lemonjelly76.musicplayer.IntegrationTestConfiguration;
import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.category.Category;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.playlist.PlaylistCopy;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistCopyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@Transactional
class JdbcPlaylistCopyRepositoryTest {

    @Autowired
    private PersonRepository people;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private PlaylistCopyRepository copies;

    @Test
    void keepsOneCopyPerPersonAndActivityIncludingGeneral() {
        Person craig = Person.create("Craig", Optional.of(Provider.TIDAL));
        people.save(craig);
        Category running = Category.create("Running", "");
        categories.save(running);
        PlaylistCopy runs = PlaylistCopy.wanted(craig.id(), Optional.of(running.id()), Provider.SPOTIFY);
        PlaylistCopy general = PlaylistCopy.wanted(craig.id(), Optional.empty(), Provider.SPOTIFY);

        copies.save(runs);
        copies.save(runs.writtenAs("sp-1"));
        copies.save(general);
        copies.save(general.writtenAs("sp-2"));

        assertThat(copies.forPerson(craig.id())).containsExactlyInAnyOrder(runs.writtenAs("sp-1"), general.writtenAs("sp-2"));
        assertThat(copies.find(craig.id(), Optional.empty())).contains(general.writtenAs("sp-2"));
        assertThat(copies.forActivity(running.id())).containsExactly(runs.writtenAs("sp-1"));
        assertThat(copies.findByPlaylist("sp-1")).contains(runs.writtenAs("sp-1"));

        copies.delete(craig.id(), Optional.of(running.id()));
        assertThat(copies.forPerson(craig.id())).containsExactly(general.writtenAs("sp-2"));
    }
}
