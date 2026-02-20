package com.certifytube.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    @Value("${auth.jwt.expiration-minutes:120}")
    private long jwtExpirationMinutes;

    public String generateToken(String email, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(jwtExpirationMinutes * 60);
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    public Instant extractExpiration(String token) {
        Date exp = extractClaims(token).getExpiration();
        return exp == null ? null : exp.toInstant();
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        String username = extractUsername(token);
        Instant expiry = extractExpiration(token);
        return username != null
                && username.equalsIgnoreCase(expectedUsername)
                && expiry != null
                && expiry.isAfter(Instant.now());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey() {
        String secret = jwtSecret == null ? "" : jwtSecret.trim();
        if (secret.length() < 32) {
            throw new IllegalStateException("auth.jwt.secret must be at least 32 characters");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
