package io.github.lemonjelly76.musicplayer.adapter.oauth;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Optional;

/**
 * Each person's tokens for their own streaming account.
 */
@Component
public class ProviderTokenStore {

    private final JdbcClient jdbc;

    ProviderTokenStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProviderTokens> find(PersonId person, Provider provider) {
        return jdbc.sql("""
                        select provider_user_id, access_token, refresh_token, expires_at from provider_account
                        where person_id = :person and provider = :provider""")
                .param("person", person.value())
                .param("provider", provider.name())
                .query((row, number) -> new ProviderTokens(
                        Optional.ofNullable(row.getString("provider_user_id")),
                        row.getString("access_token"),
                        Optional.ofNullable(row.getString("refresh_token")),
                        row.getTimestamp("expires_at").toInstant()))
                .optional();
    }

    public void save(PersonId person, Provider provider, ProviderTokens tokens) {
        jdbc.sql("""
                        insert into provider_account (person_id, provider, provider_user_id, access_token,
                            refresh_token, expires_at)
                        values (:person, :provider, :userId, :accessToken, :refreshToken, :expiresAt)
                        on conflict (person_id, provider) do update set provider_user_id = excluded.provider_user_id,
                            access_token = excluded.access_token, refresh_token = excluded.refresh_token,
                            expires_at = excluded.expires_at""")
                .param("person", person.value())
                .param("provider", provider.name())
                .param("userId", tokens.userId().orElse(null))
                .param("accessToken", tokens.accessToken())
                .param("refreshToken", tokens.refreshToken().orElse(null))
                .param("expiresAt", Timestamp.from(tokens.expiresAt()))
                .update();
    }

    public void clear(PersonId person, Provider provider) {
        jdbc.sql("delete from provider_account where person_id = :person and provider = :provider")
                .param("person", person.value())
                .param("provider", provider.name())
                .update();
    }
}
