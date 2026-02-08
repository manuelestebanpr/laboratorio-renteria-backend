package com.renteria.lims.auth.controller;

import com.renteria.lims.auth.model.dto.*;
import com.renteria.lims.auth.service.AuthService;
import com.renteria.lims.auth.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
        log.debug("Login attempt for: {}", maskEmail(request.email()));
        
        AuthService.LoginResult result = authService.login(request);
        
        // Set refresh token as HttpOnly cookie
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, result.rawRefreshToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge((int) (jwtService.getAccessTokenExpiryMs() * 7 / 1000)); // 7 days
        response.addCookie(cookie);
        
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        
        // Set new refresh token as HttpOnly cookie
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, result.rawRefreshToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge((int) (jwtService.getAccessTokenExpiryMs() * 7 / 1000)); // 7 days
        response.addCookie(cookie);
        
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        UUID userId = UUID.fromString(authentication.getName());
        String refreshToken = extractRefreshToken(request);
        
        authService.logout(userId, refreshToken);
        
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        
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
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
    
    private String maskEmail(String email) {
        if (email == null || email.length() < 5) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex < 2) return "***";
        return email.substring(0, 2) + "***@" + email.substring(atIndex + 1);
    }
}
