package io.github.lemonjelly76.musicplayer.domain.album;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

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
    void takesTheFormatsAServiceOffersItInWhenItKnowsNone() {
        AlbumRef named = AlbumRef.of("Pink Floyd", "The Dark Side of the Moon");
        AlbumRef fromTidal = new AlbumRef("Pink Floyd", "The Dark Side of the Moon", Optional.of(1973),
                ProviderIds.of(Provider.TIDAL, "t-2"), Optional.empty(),
                Set.of(AudioFormat.HI_RES, AudioFormat.DOLBY_ATMOS));

        assertThat(named.formats()).isEmpty();
        assertThat(named.enrichedWith(fromTidal).formats()).containsExactly(AudioFormat.HI_RES, AudioFormat.DOLBY_ATMOS);
        assertThat(fromTidal.withCover("https://c").formats()).hasSize(2);
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
    void ratingsRunFromDislikeToLove() {
        assertThat(Rating.of(3)).isEqualTo(Rating.LOVE);
        assertThat(Rating.parse(" Not for me ")).isEqualTo(Rating.DISLIKE);
        assertThat(Rating.parse("ok")).isEqualTo(Rating.OK);
        assertThat(Rating.OK.keeps()).isTrue();
        assertThat(Rating.DISLIKE.keeps()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> Rating.of(4));
        assertThatIllegalArgumentException().isThrownBy(() -> Rating.parse("meh")).withMessageContaining("love, ok");
    }
}
