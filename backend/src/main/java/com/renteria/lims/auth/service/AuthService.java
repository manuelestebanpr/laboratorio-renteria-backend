package com.renteria.lims.auth.service;

import com.renteria.lims.auth.model.PasswordResetToken;
import com.renteria.lims.auth.model.dto.*;
import com.renteria.lims.auth.repository.PasswordResetTokenRepository;
import com.renteria.lims.auth.repository.RefreshTokenRepository;
import com.renteria.lims.common.util.StringUtils;
import com.renteria.lims.common.util.TokenUtils;
import com.renteria.lims.config.SecurityConfigProps;
import com.renteria.lims.email.service.EmailService;
import com.renteria.lims.user.model.User;
import com.renteria.lims.user.repository.PermissionRepository;
import com.renteria.lims.user.repository.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    
    // Rate limiting buckets
    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> resetBuckets = new ConcurrentHashMap<>();

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
    public LoginResult login(LoginRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();
        
        // Check rate limit
        if (!checkLoginLimit(normalizedEmail)) {
            log.warn("Rate limit exceeded for login: {}", StringUtils.maskEmail(normalizedEmail));
            throw new BadCredentialsException("Too many login attempts. Please try again later.");
        }
        
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        
        if (userOpt.isEmpty()) {
            log.warn("Login attempt for non-existent email: {}", StringUtils.maskEmail(normalizedEmail));
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = userOpt.get();

        if (user.isLocked()) {
            log.warn("Login attempt for locked account: {}", StringUtils.maskEmail(normalizedEmail));
            throw new LockedException("Account is locked");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    normalizedEmail,
                    request.password()
                )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            user.setFailedLoginAttempts(0);
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            Set<String> permissions = permissionRepository.findEffectivePermissions(user.getId(), user.getRole().name());
            String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), permissions);
            RefreshTokenService.RefreshTokenResult refreshResult = refreshTokenService.createRefreshToken(user.getId());

            String fullName = getUserFullName(user);

            log.info("Successful login for user: {}", StringUtils.maskEmail(normalizedEmail));

            LoginResponse response = new LoginResponse(
                accessToken,
                jwtService.getAccessTokenExpiryMs() / 1000,
                user.isForcePasswordChange(),
                new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name(), fullName)
            );
            
            return new LoginResult(response, refreshResult.rawToken());

        } catch (BadCredentialsException e) {
            handleFailedLogin(user);
            throw e;
        }
    }

    @Transactional
    public RefreshResult refresh(String refreshTokenCookie) {
        if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
            throw new BadCredentialsException("Refresh token required");
        }

        Optional<RefreshTokenService.RefreshTokenResult> rotatedOpt = refreshTokenService.rotateRefreshToken(refreshTokenCookie);
        
        if (rotatedOpt.isEmpty()) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        RefreshTokenService.RefreshTokenResult rotated = rotatedOpt.get();
        User user = userRepository.findById(rotated.token().getUserId())
            .orElseThrow(() -> new BadCredentialsException("User not found"));

        Set<String> permissions = permissionRepository.findEffectivePermissions(user.getId(), user.getRole().name());
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), permissions);

        log.debug("Token refreshed for user: {}", StringUtils.maskEmail(user.getEmail()));

        RefreshResponse response = new RefreshResponse(accessToken, jwtService.getAccessTokenExpiryMs() / 1000);
        return new RefreshResult(response, rotated.rawToken());
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
        
        log.info("Password changed for user: {}", StringUtils.maskEmail(user.getEmail()));
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();
        
        // Check rate limit
        if (!checkResetLimit(normalizedEmail)) {
            log.warn("Rate limit exceeded for password reset: {}", StringUtils.maskEmail(normalizedEmail));
            return;
        }
        
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        
        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for non-existent email: {}", StringUtils.maskEmail(normalizedEmail));
            return;
        }

        User user = userOpt.get();

        long activeTokens = passwordResetTokenRepository.countActiveByUserId(user.getId());
        if (activeTokens >= securityConfig.getMaxResetTokensPerUser()) {
            log.warn("Max reset tokens reached for user: {}", StringUtils.maskEmail(user.getEmail()));
            return;
        }

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = TokenUtils.sha256Hex(rawToken);
        Instant expiresAt = Instant.now().plusMillis(securityConfig.getPasswordResetExpiryMs());

        PasswordResetToken resetToken = new PasswordResetToken(user.getId(), tokenHash, expiresAt);
        passwordResetTokenRepository.save(resetToken);

        try {
            emailService.sendPasswordReset(user.getEmail(), rawToken);
            log.info("Password reset token created and email sent for user: {}", StringUtils.maskEmail(user.getEmail()));
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", StringUtils.maskEmail(user.getEmail()), e);
            throw new RuntimeException("Failed to send password reset email. Please try again later.", e);
        }
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirm request) {
        String tokenHash = TokenUtils.sha256Hex(request.token());
        
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
        
        log.info("Password reset completed for user: {}", StringUtils.maskEmail(user.getEmail()));
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= securityConfig.getMaxLoginAttempts()) {
            user.setLockedUntil(Instant.now().plusMillis(securityConfig.getLockoutDurationMs()));
            log.warn("Account locked after {} failed attempts: {}", attempts, StringUtils.maskEmail(user.getEmail()));
            
            // Lockout email is best-effort - must NOT break login flow
            try {
                emailService.sendAccountLockout(user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send lockout notification to: {}", StringUtils.maskEmail(user.getEmail()), e);
                // Do NOT rethrow - lockout notification is secondary to the lock itself
            }
        }

        userRepository.save(user);
    }

    private String getUserFullName(User user) {
        return user.getEmail();
    }
    
    // Rate limiting methods
    private boolean checkLoginLimit(String email) {
        Bucket bucket = loginBuckets.computeIfAbsent(email.toLowerCase(), k -> createLoginBucket());
        return bucket.tryConsume(1);
    }

    private boolean checkResetLimit(String email) {
        Bucket bucket = resetBuckets.computeIfAbsent(email.toLowerCase(), k -> createResetBucket());
        return bucket.tryConsume(1);
    }

    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(15)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createResetBucket() {
        Bandwidth limit = Bandwidth.classic(3, Refill.intervally(3, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }
    
    public record LoginResult(LoginResponse response, String rawRefreshToken) {}
    public record RefreshResult(RefreshResponse response, String rawRefreshToken) {}
}
