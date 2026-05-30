package com.gateflow.victor.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT authentication filter — runs once per request before controllers.
 * Extracts Bearer token, validates it, and sets Spring Security context.
 * <p>
 * SDK endpoints (/api/v1/bucketing, /api/v1/config, /api/v1/events) also accept
 * an {@code X-Api-Key} header as an alternative authentication method.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            List<String> roles = jwtTokenProvider.getRoles(token);

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        // For SDK endpoints, also accept X-Api-Key header
        String path = request.getRequestURI();
        if ((path.startsWith("/api/v1/bucketing") || path.startsWith("/api/v1/config")
                || path.startsWith("/api/v1/events")) && token == null) {
            String apiKey = request.getHeader("X-Api-Key");
            if (StringUtils.hasText(apiKey)) {
                // API key authenticates as a system user with SDK_CLIENT role
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(0L, apiKey,
                                List.of(new SimpleGrantedAuthority("ROLE_SDK_CLIENT")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
