package io.github.lemonjelly76.musicplayer.domain.candidate;

import java.util.regex.Pattern;

/**
 * Releases that are no new album to try: DJ mixes, outtakes, remix albums, live recordings and best-ofs.
 */
final class Filler {

    private static final Pattern FILLER = Pattern.compile(
            "\\b(dj mix|mixed by|fabric presents|outtakes|remixed|remixes|live at|live from|live in|greatest hits"
                    + "|best of|b-sides|rarities|karaoke)\\b|[(\\[]live[)\\]]",
            Pattern.CASE_INSENSITIVE);

    private Filler() {
    }

    static boolean is(String title) {
        return FILLER.matcher(title).find();
    }
}
