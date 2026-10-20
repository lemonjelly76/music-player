package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PersonServiceTest {

    private final World world = new World();

    @Test
    void findsPeopleByNameIgnoringCaseOrById() {
        Person craig = world.people.add("Craig", Optional.of(Provider.TIDAL));

        assertThat(world.people.find("craig")).isEqualTo(craig);
        assertThat(world.people.find(craig.id().toString())).isEqualTo(craig);
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> world.people.find("Sam"))
                .withMessageContaining("list_people");
    }

    @Test
    void refusesASecondPersonWithTheSameName() {
        world.people.add("Craig", Optional.empty());

        assertThatIllegalArgumentException().isThrownBy(() -> world.people.add(" CRAIG", Optional.empty()));
    }

    @Test
    void allowsAtMostFivePeople() {
        for (String name : new String[]{"A", "B", "C", "D", "E"}) {
            world.people.add(name, Optional.empty());
        }

        assertThatIllegalArgumentException().isThrownBy(() -> world.people.add("F", Optional.empty()))
                .withMessageContaining("at most 5");
    }

    @Test
    void listsPeopleByName() {
        world.people.add("Sam", Optional.empty());
        world.people.add("Alex", Optional.empty());

        assertThat(world.people.all()).extracting(Person::name).containsExactly("Alex", "Sam");
    }
}
