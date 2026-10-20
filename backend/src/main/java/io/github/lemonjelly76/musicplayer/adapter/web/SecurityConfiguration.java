package io.github.lemonjelly76.musicplayer.adapter.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * One household login from APP_USERNAME and APP_PASSWORD guards the API and /mcp with HTTP Basic (ADR-011): the
 * Android app and Claude Desktop's bridge both send it on every call. No browser page uses cookies, so there is no
 * session and nothing for CSRF to protect. When MCP_TOKEN is set, a claude.ai connector may instead present it as a
 * bearer token on /mcp. The streaming services' sign-in callback stays open; the state it carries is the check.
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration {

    static final String MCP_ENDPOINT = "/mcp";
    static final String CONNECT_CALLBACK = "/api/connect/*/callback";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${app.security.mcp-token:}") String mcpToken)
            throws Exception {
        if (!mcpToken.isBlank()) {
            http.addFilterBefore(new McpTokenAuthenticationFilter(MCP_ENDPOINT, mcpToken), BasicAuthenticationFilter.class);
        }
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName("Music Player");
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, CONNECT_CALLBACK).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${app.security.username:}") String username,
            @Value("${app.security.password:}") String password) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Set APP_USERNAME and APP_PASSWORD to protect the app");
        }
        String encoded = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password);
        return new InMemoryUserDetailsManager(User.withUsername(username).password(encoded).roles("USER").build());
    }
}
