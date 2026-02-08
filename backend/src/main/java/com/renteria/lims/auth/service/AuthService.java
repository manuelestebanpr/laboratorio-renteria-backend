package com.renteria.lims.auth.service;

import com.renteria.lims.auth.model.PasswordResetToken;
import com.renteria.lims.auth.model.RefreshToken;
import com.renteria.lims.auth.model.dto.*;
import com.renteria.lims.auth.repository.PasswordResetTokenRepository;
import com.renteria.lims.auth.repository.RefreshTokenRepository;
import com.renteria.lims.config.JwtConfig;
import com.renteria.lims.config.SecurityConfigProps;
import com.renteria.lims.email.service.EmailService;
import com.renteria.lims.user.model.Role;
import com.renteria.lims.user.model.User;
import com.renteria.lims.user.repository.PermissionRepository;
import com.renteria.lims.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityConfigProps securityConfig;
    private final EmailService emailService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       PermissionRepository permissionRepository,
                       RefreshTokenService refreshTokenService,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       SecurityConfigProps securityConfig,
                       EmailService emailService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.securityConfig = securityConfig;
        this.emailService = emailService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.email().toLowerCase().trim());
        
        if (userOpt.isEmpty()) {
            log.warn("Login attempt for non-existent email: {}", maskEmail(request.email()));
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = userOpt.get();

        if (user.isLocked()) {
            log.warn("Login attempt for locked account: {}", maskEmail(request.email()));
            throw new LockedException("Account is locked");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.email().toLowerCase().trim(),
                    request.password()
                )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            user.setFailedLoginAttempts(0);
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            Set<String> permissions = permissionRepository.findEffectivePermissions(user.getId(), user.getRole().name());
            String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), permissions);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

            String fullName = getUserFullName(user);

            log.info("Successful login for user: {}", maskEmail(request.email()));

            return new LoginResponse(
                accessToken,
                jwtService.getAccessTokenExpiryMs() / 1000,
                user.isForcePasswordChange(),
                new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name(), fullName)
            );

        } catch (BadCredentialsException e) {
            handleFailedLogin(user);
            throw e;
        }
    }

    @Transactional
    public RefreshResponse refresh(String refreshTokenCookie) {
        if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
            throw new BadCredentialsException("Refresh token required");
        }

        Optional<RefreshToken> rotatedOpt = refreshTokenService.rotateRefreshToken(refreshTokenCookie);
        
        if (rotatedOpt.isEmpty()) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        RefreshToken rotated = rotatedOpt.get();
        User user = userRepository.findById(rotated.getUserId())
            .orElseThrow(() -> new BadCredentialsException("User not found"));

        Set<String> permissions = permissionRepository.findEffectivePermissions(user.getId(), user.getRole().name());
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), permissions);

        log.debug("Token refreshed for user: {}", maskEmail(user.getEmail()));

        return new RefreshResponse(accessToken, jwtService.getAccessTokenExpiryMs() / 1000);
    }

    @Transactional
    public void logout(UUID userId, String refreshTokenCookie) {
        if (refreshTokenCookie != null && !refreshTokenCookie.isBlank()) {
            refreshTokenService.rotateRefreshToken(refreshTokenCookie);
        }
        refreshTokenService.revokeAllUserTokens(userId);
        SecurityContextHolder.clearContext();
        log.info("Logout for user: {}", userId);
    }

    @Transactional
    public void changePassword(UUID userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        String newPasswordHash = passwordEncoder.encode(request.newPassword());
        user.setPasswordHash(newPasswordHash);
        user.setForcePasswordChange(false);
        userRepository.save(user);

        refreshTokenService.revokeAllUserTokens(userId);
        
        log.info("Password changed for user: {}", maskEmail(user.getEmail()));
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.email().toLowerCase().trim());
        
        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for non-existent email: {}", maskEmail(request.email()));
            return;
        }

        User user = userOpt.get();

        long activeTokens = passwordResetTokenRepository.countActiveByUserId(user.getId());
        if (activeTokens >= securityConfig.getMaxResetTokensPerUser()) {
            log.warn("Max reset tokens reached for user: {}", maskEmail(user.getEmail()));
            return;
        }

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plusMillis(securityConfig.getPasswordResetExpiryMs());

        PasswordResetToken resetToken = new PasswordResetToken(user.getId(), tokenHash, expiresAt);
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordReset(user.getEmail(), rawToken);
        
        log.info("Password reset token created for user: {}", maskEmail(user.getEmail()));
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirm request) {
        String tokenHash = hashToken(request.token());
        
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new IllegalStateException("Invalid or expired reset token"));

        if (!resetToken.isValid()) {
            throw new IllegalStateException("Reset token is invalid or expired");
        }

        User user = userRepository.findById(resetToken.getUserId())
            .orElseThrow(() -> new IllegalStateException("User not found"));

        String newPasswordHash = passwordEncoder.encode(request.newPassword());
        user.setPasswordHash(newPasswordHash);
        user.setForcePasswordChange(false);
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        refreshTokenService.revokeAllUserTokens(user.getId());
        
        log.info("Password reset completed for user: {}", maskEmail(user.getEmail()));
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= securityConfig.getMaxLoginAttempts()) {
            user.setLockedUntil(Instant.now().plusMillis(securityConfig.getLockoutDurationMs()));
            log.warn("Account locked after {} failed attempts: {}", attempts, maskEmail(user.getEmail()));
            emailService.sendAccountLockout(user.getEmail());
        }

        userRepository.save(user);
    }

    private String getUserFullName(User user) {
        return user.getEmail();
    }

    private String maskEmail(String email) {
        if (email == null || email.length() < 5) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex < 2) return "***";
        return email.substring(0, 2) + "***@" + email.substring(atIndex + 1);
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
