package io.github.lemonjelly76.musicplayer.domain.plays;

/**
 * How many tracks a person has played by an artist, across how many of their albums.
 */
public record ArtistPlays(String artist, int plays, int albums) {
}
