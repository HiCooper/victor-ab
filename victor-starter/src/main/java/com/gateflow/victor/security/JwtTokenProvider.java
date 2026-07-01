package com.gateflow.victor.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * JWT token provider — create, validate, and parse tokens.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** 内置的不安全默认密钥；仅允许在 dev/test/local 或无 profile 时使用（并告警）。 */
    static final String INSECURE_DEFAULT_SECRET = "victor-jwt-secret-key-min-32-chars!!";

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(@Value("${victor.security.jwt.secret:" + INSECURE_DEFAULT_SECRET + "}") String secret,
                            @Value("${victor.security.jwt.expiration-ms:86400000}") long expirationMs,
                            Environment environment) {
        if (INSECURE_DEFAULT_SECRET.equals(secret)) {
            String[] profiles = environment.getActiveProfiles();
            boolean explicitNonDev = profiles.length > 0 && Arrays.stream(profiles).noneMatch(p ->
                    p.equalsIgnoreCase("dev") || p.equalsIgnoreCase("test") || p.equalsIgnoreCase("local"));
            if (explicitNonDev) {
                // 生产等非开发环境使用内置默认密钥 → 任何人都能伪造管理员 token，拒绝启动
                throw new IllegalStateException(
                        "victor.security.jwt.secret is the built-in INSECURE default; set VICTOR_JWT_SECRET "
                                + "(>=32 chars) for non-dev profiles: " + Arrays.toString(profiles));
            }
            log.warn("Using the built-in INSECURE default JWT secret — set VICTOR_JWT_SECRET for any real deployment.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Long getUserId(String token) {
        String subject = parseClaims(token).getSubject();
        return Long.parseLong(subject);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return parseClaims(token).get("roles", List.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
