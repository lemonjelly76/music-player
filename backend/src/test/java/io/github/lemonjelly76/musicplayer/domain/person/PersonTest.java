package io.github.lemonjelly76.musicplayer.domain.person;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PersonTest {

    @Test
    void startsWithPlayTrackingOnAndIsFoundByNameIgnoringCase() {
        Person craig = Person.create(" Craig ", Optional.empty());

        assertThat(craig.name()).isEqualTo("Craig");
        assertThat(craig.playTracking()).isTrue();
        assertThat(craig.isCalled("craig")).isTrue();
    }

    @Test
    void needsANameAndDropsABlankLastFmUser() {
        assertThatIllegalArgumentException().isThrownBy(() -> Person.create("", Optional.empty()));
        assertThat(Person.create("Sam", Optional.empty()).withLastFmUser(Optional.of(" ")).lastFmUser()).isEmpty();
    }
}
