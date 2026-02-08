package com.renteria.lims.auth.service;

import com.renteria.lims.auth.dto.LoginResponse;
import com.renteria.lims.auth.model.RefreshToken;
import com.renteria.lims.auth.repository.RefreshTokenRepository;
import com.renteria.lims.config.AppProperties;
import com.renteria.lims.config.jwt.JwtService;
import com.renteria.lims.exception.ApiException;
import com.renteria.lims.user.model.User;
import com.renteria.lims.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserService userService,
                               JwtService jwtService,
                               AppProperties appProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userService = userService;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public String createRefreshToken(User user, String ipAddress, String userAgent) {
        String tokenValue = generateSecureToken();
        String tokenHash = hashToken(tokenValue);
        String family = UUID.randomUUID().toString();

        Instant expiresAt = Instant.now().plus(appProperties.getRefreshToken().getExpirationDays(), ChronoUnit.DAYS);

        RefreshToken refreshToken = new RefreshToken(
                tokenHash,
                user,
                family,
                expiresAt,
                ipAddress,
                userAgent
        );

        refreshTokenRepository.save(refreshToken);
        logger.debug("Created refresh token for user: {}", user.getUsername());

        return tokenValue;
    }

    @Transactional
    public LoginResponse rotateRefreshToken(String tokenValue, String ipAddress, String userAgent) {
        String tokenHash = hashToken(tokenValue);

        RefreshToken existingToken = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                .orElseThrow(() -> new ApiException("REFRESH_TOKEN_INVALID", "Invalid refresh token"));

        // Check if token is revoked - this could be token theft
        if (existingToken.isRevoked()) {
            logger.warn("Potential token theft detected for user: {}", existingToken.getUser().getUsername());
            // Revoke entire token family
            refreshTokenRepository.revokeAllByFamily(existingToken.getFamily(), Instant.now());
            throw new ApiException("REFRESH_TOKEN_INVALID", "Token has been revoked due to security concerns");
        }

        // Check if token is expired
        if (existingToken.isExpired()) {
            logger.debug("Expired refresh token used for user: {}", existingToken.getUser().getUsername());
            throw new ApiException("TOKEN_EXPIRED", "Refresh token has expired");
        }

        User user = existingToken.getUser();

        // Check if user is still active
        if (!user.isActive()) {
            throw new ApiException("ACCOUNT_DISABLED", "Account is disabled");
        }

        // Create new refresh token (rotation)
        String newTokenValue = generateSecureToken();
        String newTokenHash = hashToken(newTokenValue);
        Instant expiresAt = Instant.now().plus(appProperties.getRefreshToken().getExpirationDays(), ChronoUnit.DAYS);

        RefreshToken newToken = new RefreshToken(
                newTokenHash,
                user,
                existingToken.getFamily(),
                expiresAt,
                ipAddress,
                userAgent
        );

        refreshTokenRepository.save(newToken);

        // Mark old token as replaced
        refreshTokenRepository.markAsReplaced(tokenHash, newTokenHash, Instant.now());

        // Generate new access token
        Set<String> permissions = userService.getUserPermissions(user.getId(), user.getRole());
        String accessToken = jwtService.generateToken(
                user.getUsername(),
                user.getId(),
                permissions,
                user.isForcePasswordChange()
        );

        logger.debug("Rotated refresh token for user: {}", user.getUsername());

        return new LoginResponse(
                accessToken,
                newTokenValue,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                permissions,
                user.isForcePasswordChange(),
                null
        );
    }

    @Transactional
    public void revokeToken(String tokenValue) {
        String tokenHash = hashToken(tokenValue);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElse(null);

        if (token != null && !token.isRevoked()) {
            token.revoke();
            refreshTokenRepository.save(token);
            logger.debug("Revoked refresh token for user: {}", token.getUser().getUsername());
        }
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        logger.debug("Revoked all refresh tokens for user: {}", userId);
    }

    @Transactional
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        refreshTokenRepository.deleteExpiredTokens(cutoff);
        logger.debug("Cleaned up expired refresh tokens");
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
