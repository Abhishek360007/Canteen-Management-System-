package com.tulas.canteen.security;

import com.tulas.canteen.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration expiry;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiry-hours}") long expiryHours) {
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            log.warn("JWT_SECRET is not set: using a random secret. Everyone is logged out when the server restarts. "
                    + "Set JWT_SECRET (32+ characters) to keep logins across restarts.");
            this.key = Keys.hmacShaKeyFor(random);
        } else {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
            }
            this.key = Keys.hmacShaKeyFor(bytes);
        }
        this.expiry = Duration.ofHours(expiryHours);
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .claim("ver", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
    }

    /** What a valid token says: whose it is and which password "version" it was issued for. */
    public record TokenInfo(Long userId, int version) {}

    /** Returns the contents of a valid, unexpired token. */
    public Optional<TokenInfo> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Number ver = claims.get("ver", Number.class);
            return Optional.of(new TokenInfo(Long.valueOf(claims.getSubject()), ver == null ? 0 : ver.intValue()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
