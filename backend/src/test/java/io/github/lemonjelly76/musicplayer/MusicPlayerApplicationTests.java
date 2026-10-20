package io.github.lemonjelly76.musicplayer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
class MusicPlayerApplicationTests {

    @Test
    void contextLoads() {
    }
}
