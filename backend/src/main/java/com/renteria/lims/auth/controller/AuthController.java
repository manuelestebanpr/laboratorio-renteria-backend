package com.renteria.lims.auth.controller;

import com.renteria.lims.auth.model.dto.*;
import com.renteria.lims.auth.service.AuthService;
import com.renteria.lims.auth.service.JwtService;
import com.renteria.lims.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.debug("Login attempt for: {}", StringUtils.maskEmail(request.email()));
        
        AuthService.LoginResult result = authService.login(request);
        
        // Set refresh token as HttpOnly cookie with SameSite=Strict
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, result.rawRefreshToken())
            .httpOnly(true)
            .secure(true)
            .path("/api/v1/auth")
            .maxAge(Duration.ofDays(7))
            .sameSite("Strict")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        
        // Set new refresh token as HttpOnly cookie with SameSite=Strict
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, result.rawRefreshToken())
            .httpOnly(true)
            .secure(true)
            .path("/api/v1/auth")
            .maxAge(Duration.ofDays(7))
            .sameSite("Strict")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        UUID userId = UUID.fromString(authentication.getName());
        String refreshToken = extractRefreshToken(request);
        
        authService.logout(userId, refreshToken);
        
        // Clear refresh token cookie with SameSite=Strict
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(true)
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO)
            .sameSite("Strict")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeRequest request) {
        
        UUID userId = UUID.fromString(authentication.getName());
        authService.changePassword(userId, request);
        
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<MessageResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        
        return ResponseEntity.ok(new MessageResponse(
            "If an account exists with this email, a reset link has been sent."
        ));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirm request) {
        authService.confirmPasswordReset(request);
        
        return ResponseEntity.noContent().build();
    }

    private String extractRefreshToken(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
