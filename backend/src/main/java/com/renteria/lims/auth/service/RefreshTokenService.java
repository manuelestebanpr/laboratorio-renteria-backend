package com.renteria.lims.auth.service;

import com.renteria.lims.auth.model.RefreshToken;
import com.renteria.lims.auth.repository.RefreshTokenRepository;
import com.renteria.lims.config.JwtConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtConfig jwtConfig;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtConfig jwtConfig) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtConfig = jwtConfig;
    }

    @Transactional
    public RefreshToken createRefreshToken(UUID userId) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusMillis(jwtConfig.getRefreshTokenExpiryMs());

        RefreshToken refreshToken = new RefreshToken(userId, tokenHash, familyId, expiresAt);
        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        
        log.debug("Created refresh token for user {} with family {}", userId, familyId);
        return saved;
    }

    @Transactional
    public Optional<RefreshToken> rotateRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        
        Optional<RefreshToken> existingOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (existingOpt.isEmpty()) {
            log.warn("Refresh token not found");
            return Optional.empty();
        }

        RefreshToken existing = existingOpt.get();

        if (existing.isRevoked()) {
            log.warn("Refresh token reuse detected - revoking entire family {}", existing.getFamilyId());
            revokeFamily(existing.getFamilyId());
            return Optional.empty();
        }

        if (!existing.isValid()) {
            log.warn("Refresh token expired or invalid");
            return Optional.empty();
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        String newRawToken = UUID.randomUUID().toString();
        String newTokenHash = hashToken(newRawToken);
        Instant newExpiresAt = Instant.now().plusMillis(jwtConfig.getRefreshTokenExpiryMs());

        RefreshToken newToken = new RefreshToken(
            existing.getUserId(),
            newTokenHash,
            existing.getFamilyId(),
            newExpiresAt
        );

        RefreshToken saved = refreshTokenRepository.save(newToken);
        log.debug("Rotated refresh token for user {}", existing.getUserId());
        
        return Optional.of(saved);
    }

    @Transactional
    public void revokeFamily(UUID familyId) {
        int revoked = refreshTokenRepository.revokeByFamilyId(familyId);
        log.info("Revoked {} tokens in family {}", revoked, familyId);
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked {} tokens for user {}", revoked, userId);
    }

    public String getRawToken(RefreshToken refreshToken) {
        return refreshToken.getTokenHash();
    }

    String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
