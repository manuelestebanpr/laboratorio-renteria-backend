package com.renteria.lims.auth.service;

import com.renteria.lims.config.JwtConfig;
import com.renteria.lims.user.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtConfig jwtConfig;
    private final SecretKey signingKey;

    public JwtService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtConfig.getSecret()));
    }

    public String generateAccessToken(UUID userId, String email, Role role, Set<String> permissions) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtConfig.getAccessTokenExpiryMs());

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role.name())
            .claim("permissions", permissions)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey)
            .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }

    public UUID extractUserId(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return null;
        return UUID.fromString(claims.getSubject());
    }

    public String extractEmail(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return null;
        return claims.get("email", String.class);
    }

    public Role extractRole(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return null;
        return Role.valueOf(claims.get("role", String.class));
    }

    @SuppressWarnings("unchecked")
    public Set<String> extractPermissions(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return Set.of();
        return claims.get("permissions", Set.class);
    }

    public boolean isTokenValid(String token) {
        return validateToken(token) != null;
    }

    public long getAccessTokenExpiryMs() {
        return jwtConfig.getAccessTokenExpiryMs();
    }
}
