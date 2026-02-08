package com.renteria.lims.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.security")
public class SecurityConfigProps {
    
    private int maxLoginAttempts;
    private long lockoutDurationMs;
    private long passwordResetExpiryMs;
    private int maxResetTokensPerUser;
    
    public int getMaxLoginAttempts() {
        return maxLoginAttempts;
    }
    
    public void setMaxLoginAttempts(int maxLoginAttempts) {
        this.maxLoginAttempts = maxLoginAttempts;
    }
    
    public long getLockoutDurationMs() {
        return lockoutDurationMs;
    }
    
    public void setLockoutDurationMs(long lockoutDurationMs) {
        this.lockoutDurationMs = lockoutDurationMs;
    }
    
    public long getPasswordResetExpiryMs() {
        return passwordResetExpiryMs;
    }
    
    public void setPasswordResetExpiryMs(long passwordResetExpiryMs) {
        this.passwordResetExpiryMs = passwordResetExpiryMs;
    }
    
    public int getMaxResetTokensPerUser() {
        return maxResetTokensPerUser;
    }
    
    public void setMaxResetTokensPerUser(int maxResetTokensPerUser) {
        this.maxResetTokensPerUser = maxResetTokensPerUser;
    }
}
