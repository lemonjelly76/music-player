package io.github.lemonjelly76.musicplayer.domain.album;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Formats kept in their declared order, so lists of them read the same everywhere.
 */
public final class AudioFormats {

    private AudioFormats() {
    }

    public static Set<AudioFormat> copyOf(Set<AudioFormat> formats) {
        Objects.requireNonNull(formats, "formats");
        return formats.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(formats));
    }
}
