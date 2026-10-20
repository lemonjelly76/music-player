package io.github.lemonjelly76.musicplayer.domain.port;

public class MusicProviderUnavailableException extends RuntimeException {

    public MusicProviderUnavailableException(String message) {
        super(message);
    }

    public MusicProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
