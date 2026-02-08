package com.renteria.lims.auth.service;

import com.renteria.lims.config.JwtConfig;
import com.renteria.lims.user.model.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setSecret(Base64.getEncoder().encodeToString("test-secret-minimum-32-chars-long!!".getBytes()));
        config.setAccessTokenExpiryMs(900000);
        config.setRefreshTokenExpiryMs(604800000);
        jwtService = new JwtService(config);
    }

    @Test
    void generateAccessToken_withValidData_returnsToken() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        Role role = Role.PATIENT;
        Set<String> permissions = Set.of("OWN_PROFILE_VIEW");

        String token = jwtService.generateAccessToken(userId, email, role, permissions);

        assertNotNull(token);
        assertTrue(token.length() > 50);
    }

    @Test
    void validateToken_withValidToken_returnsClaims() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        Role role = Role.PATIENT;
        Set<String> permissions = Set.of("OWN_PROFILE_VIEW");
        String token = jwtService.generateAccessToken(userId, email, role, permissions);

        Claims claims = jwtService.validateToken(token);

        assertNotNull(claims);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(email, claims.get("email"));
        assertEquals(role.name(), claims.get("role"));
    }

    @Test
    void validateToken_withInvalidToken_returnsNull() {
        Claims claims = jwtService.validateToken("invalid.token.here");

        assertNull(claims);
    }

    @Test
    void extractUserId_withValidToken_returnsUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "test@example.com", Role.PATIENT, Set.of());

        UUID extracted = jwtService.extractUserId(token);

        assertEquals(userId, extracted);
    }

    @Test
    void extractEmail_withValidToken_returnsEmail() {
        String email = "test@example.com";
        String token = jwtService.generateAccessToken(UUID.randomUUID(), email, Role.PATIENT, Set.of());

        String extracted = jwtService.extractEmail(token);

        assertEquals(email, extracted);
    }

    @Test
    void isTokenValid_withValidToken_returnsTrue() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "test@example.com", Role.PATIENT, Set.of());

        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_withInvalidToken_returnsFalse() {
        assertFalse(jwtService.isTokenValid("invalid"));
    }
}
