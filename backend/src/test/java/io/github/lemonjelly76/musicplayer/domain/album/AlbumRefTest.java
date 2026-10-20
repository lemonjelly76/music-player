package io.github.lemonjelly76.musicplayer.domain.album;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AlbumRefTest {

    @Test
    void needsAnArtistAndATitle() {
        assertThatIllegalArgumentException().isThrownBy(() -> AlbumRef.of(" ", "Blue"));
        assertThatIllegalArgumentException().isThrownBy(() -> AlbumRef.of("Joni Mitchell", ""));
    }

    @Test
    void enrichingKeepsItsNamesAndFillsGapsFromTheOther() {
        AlbumRef fromLastFm = new AlbumRef("Joni Mitchell", "Blue", Optional.empty(), ProviderIds.musicBrainz("mb-1"));
        AlbumRef fromTidal = new AlbumRef("Joni Mitchell", "Blue (2021 Remaster)", Optional.of(1971),
                ProviderIds.of(Provider.TIDAL, "t-1"));

        AlbumRef merged = fromLastFm.enrichedWith(fromTidal);

        assertThat(merged.title()).isEqualTo("Blue");
        assertThat(merged.year()).contains(1971);
        assertThat(merged.ids()).isEqualTo(new ProviderIds(Optional.of("t-1"), Optional.empty(), Optional.of("mb-1")));
        assertThat(merged.sameAlbumAs(fromTidal)).isTrue();
    }

    @Test
    void knowsItsDecade() {
        assertThat(new AlbumRef("Oasis", "Definitely Maybe", Optional.of(1994), ProviderIds.NONE).decade())
                .contains(1990);
        assertThat(AlbumRef.of("Oasis", "Definitely Maybe").decade()).isEmpty();
    }

    @Test
    void providerIdsIgnoreBlanksAndLookUpByProvider() {
        ProviderIds ids = new ProviderIds(Optional.of(" "), Optional.of("sp-1"), Optional.empty());

        assertThat(ids.get(Provider.TIDAL)).isEmpty();
        assertThat(ids.get(Provider.SPOTIFY)).contains("sp-1");
        assertThat(ids.with(Provider.TIDAL, "t-9").tidal()).contains("t-9");
    }

    @Test
    void providersParseFromTheirPrefix() {
        assertThat(Provider.parse(" Tidal ")).isEqualTo(Provider.TIDAL);
        assertThatIllegalArgumentException().isThrownBy(() -> Provider.parse("deezer"))
                .withMessageContaining("tidal or spotify");
    }

    @Test
    void ratingsRunFromOneToFive() {
        assertThat(new Rating(5).value()).isEqualTo(5);
        assertThatIllegalArgumentException().isThrownBy(() -> new Rating(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Rating(6));
    }
}
