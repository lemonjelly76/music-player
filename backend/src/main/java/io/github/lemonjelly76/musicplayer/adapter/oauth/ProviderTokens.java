package io.github.lemonjelly76.musicplayer.adapter.oauth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public record ProviderTokens(Optional<String> userId, String accessToken, Optional<String> refreshToken,
                             Instant expiresAt) {

    boolean expireWithin(Duration margin, Instant now) {
        return expiresAt.minus(margin).isBefore(now);
    }
}
