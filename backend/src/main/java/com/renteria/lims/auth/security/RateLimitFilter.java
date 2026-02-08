package com.renteria.lims.auth.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// DISABLED: Rate limiting moved to AuthService for proper JSON body handling
// This filter is kept for reference but not registered as a Spring component
// @Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> resetBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && path.equals("/api/v1/auth/login")) {
            String email = request.getParameter("email");
            if (email != null && !checkLoginLimit(email, response)) {
                return;
            }
        }

        if ("POST".equals(method) && path.equals("/api/v1/auth/password-reset/request")) {
            String email = request.getParameter("email");
            if (email != null && !checkResetLimit(email, response)) {
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean checkLoginLimit(String email, HttpServletResponse response) throws IOException {
        Bucket bucket = loginBuckets.computeIfAbsent(email.toLowerCase(), k -> createLoginBucket());
        
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for login: {}", email);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Too many login attempts. Please try again later.\"}");
            return false;
        }
        return true;
    }

    private boolean checkResetLimit(String email, HttpServletResponse response) throws IOException {
        Bucket bucket = resetBuckets.computeIfAbsent(email.toLowerCase(), k -> createResetBucket());
        
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for password reset: {}", email);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Too many reset requests. Please try again later.\"}");
            return false;
        }
        return true;
    }

    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(15)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createResetBucket() {
        Bandwidth limit = Bandwidth.classic(3, Refill.intervally(3, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
