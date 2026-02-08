package com.renteria.lims.email.service;

public interface EmailService {
    
    void sendInitialPassword(String to, String temporaryPassword);
    
    void sendPasswordReset(String to, String resetToken);
    
    void sendAccountLockout(String to);
}
