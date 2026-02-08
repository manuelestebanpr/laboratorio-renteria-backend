package com.renteria.lims.auth.service;

import com.renteria.lims.auth.model.RefreshToken;
import com.renteria.lims.auth.repository.RefreshTokenRepository;
import com.renteria.lims.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setRefreshTokenExpiryMs(604800000);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, config);
    }

    @Test
    void createRefreshToken_withUserId_savesAndReturnsTokenResult() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.RefreshTokenResult result = refreshTokenService.createRefreshToken(userId);

        assertNotNull(result);
        assertNotNull(result.rawToken());
        assertNotNull(result.token());
        assertEquals(userId, result.token().getUserId());
        assertNotNull(result.token().getTokenHash());
        assertNotNull(result.token().getFamilyId());
        assertTrue(result.token().getExpiresAt().isAfter(Instant.now()));
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void rotateRefreshToken_withValidToken_returnsNewTokenResult() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshToken existing = new RefreshToken(userId, "hash", familyId, Instant.now().plusSeconds(3600));
        
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<RefreshTokenService.RefreshTokenResult> result = refreshTokenService.rotateRefreshToken("raw-token");

        assertTrue(result.isPresent());
        assertNotNull(result.get().rawToken());
        assertEquals(userId, result.get().token().getUserId());
        assertEquals(familyId, result.get().token().getFamilyId());
    }

    @Test
    void rotateRefreshToken_withRevokedToken_revokesFamilyAndReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshToken existing = new RefreshToken(userId, "hash", familyId, Instant.now().plusSeconds(3600));
        existing.setRevoked(true);
        
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        Optional<RefreshTokenService.RefreshTokenResult> result = refreshTokenService.rotateRefreshToken("raw-token");

        assertTrue(result.isEmpty());
        verify(refreshTokenRepository).revokeByFamilyId(familyId);
    }

    @Test
    void rotateRefreshToken_withExpiredToken_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        RefreshToken existing = new RefreshToken(userId, "hash", UUID.randomUUID(), Instant.now().minusSeconds(3600));
        
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        Optional<RefreshTokenService.RefreshTokenResult> result = refreshTokenService.rotateRefreshToken("raw-token");

        assertTrue(result.isEmpty());
    }

    @Test
    void rotateRefreshToken_withNonExistentToken_returnsEmpty() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        Optional<RefreshTokenService.RefreshTokenResult> result = refreshTokenService.rotateRefreshToken("raw-token");

        assertTrue(result.isEmpty());
    }

    @Test
    void revokeAllUserTokens_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.revokeAllByUserId(userId)).thenReturn(3);

        refreshTokenService.revokeAllUserTokens(userId);

        verify(refreshTokenRepository).revokeAllByUserId(userId);
    }
}
