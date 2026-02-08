package com.renteria.lims.auth.service;

import com.renteria.lims.auth.model.RefreshToken;
import com.renteria.lims.auth.repository.RefreshTokenRepository;
import com.renteria.lims.common.util.TokenUtils;
import com.renteria.lims.config.JwtConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    public RefreshTokenResult createRefreshToken(UUID userId) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = TokenUtils.sha256Hex(rawToken);
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusMillis(jwtConfig.getRefreshTokenExpiryMs());

        RefreshToken refreshToken = new RefreshToken(userId, tokenHash, familyId, expiresAt);
        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        
        log.debug("Created refresh token for user {} with family {}", userId, familyId);
        return new RefreshTokenResult(saved, rawToken);
    }

    @Transactional
    public Optional<RefreshTokenResult> rotateRefreshToken(String rawToken) {
        String tokenHash = TokenUtils.sha256Hex(rawToken);
        
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
        String newTokenHash = TokenUtils.sha256Hex(newRawToken);
        Instant newExpiresAt = Instant.now().plusMillis(jwtConfig.getRefreshTokenExpiryMs());

        RefreshToken newToken = new RefreshToken(
            existing.getUserId(),
            newTokenHash,
            existing.getFamilyId(),
            newExpiresAt
        );

        RefreshToken saved = refreshTokenRepository.save(newToken);
        log.debug("Rotated refresh token for user {}", existing.getUserId());
        
        return Optional.of(new RefreshTokenResult(saved, newRawToken));
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

    public String getTokenHash(String rawToken) {
        return TokenUtils.sha256Hex(rawToken);
    }

    public record RefreshTokenResult(RefreshToken token, String rawToken) {}
}
