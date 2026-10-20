package io.github.lemonjelly76.musicplayer.adapter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Lets a claude.ai custom connector, which sends a fixed header rather than a Basic login, reach the MCP endpoint
 * with {@code Authorization: Bearer <MCP_TOKEN>} (ADR-011). The token opens nothing else.
 */
class McpTokenAuthenticationFilter extends OncePerRequestFilter {

    static final int MIN_LENGTH = 32;

    private static final String BEARER = "Bearer ";

    private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository repository = new RequestAttributeSecurityContextRepository();
    private final RequestMatcher endpoint;
    private final byte[] token;

    McpTokenAuthenticationFilter(String endpoint, String token) {
        if (token.length() < MIN_LENGTH) {
            throw new IllegalStateException("MCP_TOKEN must be at least " + MIN_LENGTH + " characters; "
                    + "generate one with: openssl rand -hex 32");
        }
        this.endpoint = PathPatternRequestMatcher.withDefaults().matcher(endpoint);
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !endpoint.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER) && matches(header.substring(BEARER.length()))) {
            signIn(request, response);
        }
        chain.doFilter(request, response);
    }

    /**
     * Saved to the request as well as set, so a streamed answer's later asynchronous dispatch is still signed in.
     */
    private void signIn(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = contexts.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "claude-connector", null, AuthorityUtils.createAuthorityList("ROLE_USER")));
        contexts.setContext(context);
        repository.saveContext(context, request, response);
    }

    private boolean matches(String presented) {
        return MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
    }
}
