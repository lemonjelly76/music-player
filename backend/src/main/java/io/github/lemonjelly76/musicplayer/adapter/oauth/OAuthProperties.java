package io.github.lemonjelly76.musicplayer.adapter.oauth;

/**
 * Where a streaming service signs people in, and this app's registration with it.
 */
public record OAuthProperties(String authorizeUrl, String tokenUrl, String clientId, String clientSecret,
                              String redirectUri, String scopes) {

    public boolean registered() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
