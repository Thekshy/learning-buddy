package com.learningbuddy.security;

import com.learningbuddy.config.PropertiesConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与解析
 */
@Component
@RequiredArgsConstructor
public class JwtService {

    private final PropertiesConfig properties;
    private SecretKey key;

    private SecretKey key() {
        if (key == null) {
            byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(bytes, 0, padded, 0, bytes.length);
                bytes = padded;
            }
            key = Keys.hmacShaKeyFor(bytes);
        }
        return key;
    }

    public String issue(Long userId, String username) {
        Instant now = Instant.now();
        Instant exp = now.plus(properties.jwt().ttlHours(), ChronoUnit.HOURS);
        return Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
