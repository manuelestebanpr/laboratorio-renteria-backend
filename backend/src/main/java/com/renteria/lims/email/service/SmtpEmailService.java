package com.renteria.lims.email.service;

import com.renteria.lims.config.EmailConfig;
import com.renteria.lims.email.model.EmailTemplate;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailConfig emailConfig;

    public SmtpEmailService(JavaMailSender mailSender, TemplateEngine templateEngine, EmailConfig emailConfig) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.emailConfig = emailConfig;
    }

    @Override
    public void sendInitialPassword(String to, String temporaryPassword) {
        Context context = new Context();
        context.setVariable("temporaryPassword", temporaryPassword);
        context.setVariable("frontendUrl", emailConfig.getFrontendUrl());

        String htmlContent = templateEngine.process(EmailTemplate.INITIAL_PASSWORD.getTemplateName(), context);
        
        try {
            sendHtmlEmail(to, EmailTemplate.INITIAL_PASSWORD.getSubject(), htmlContent);
            log.info("Initial password email sent to: {}", maskEmail(to));
        } catch (Exception e) {
            log.error("Failed to send initial password email to: {}", maskEmail(to), e);
            throw new RuntimeException("Failed to send initial password email", e);
        }
    }

    @Override
    public void sendPasswordReset(String to, String resetToken) {
        Context context = new Context();
        context.setVariable("resetToken", resetToken);
        context.setVariable("resetUrl", emailConfig.getFrontendUrl() + "/reset-password?token=" + resetToken);
        context.setVariable("expiryHours", 1);

        String htmlContent = templateEngine.process(EmailTemplate.PASSWORD_RESET.getTemplateName(), context);
        
        try {
            sendHtmlEmail(to, EmailTemplate.PASSWORD_RESET.getSubject(), htmlContent);
            log.info("Password reset email sent to: {}", maskEmail(to));
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", maskEmail(to), e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    @Override
    public void sendAccountLockout(String to) {
        Context context = new Context();
        context.setVariable("frontendUrl", emailConfig.getFrontendUrl());
        context.setVariable("lockoutMinutes", 15);

        String htmlContent = templateEngine.process(EmailTemplate.ACCOUNT_LOCKOUT.getTemplateName(), context);
        
        try {
            sendHtmlEmail(to, EmailTemplate.ACCOUNT_LOCKOUT.getSubject(), htmlContent);
            log.info("Account lockout email sent to: {}", maskEmail(to));
        } catch (Exception e) {
            log.error("Failed to send account lockout email to: {}", maskEmail(to), e);
            throw new RuntimeException("Failed to send account lockout email", e);
        }
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(emailConfig.getFrom());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    private String maskEmail(String email) {
        if (email == null || email.length() < 5) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex < 2) return "***";
        return email.substring(0, 2) + "***@" + email.substring(atIndex + 1);
    }
}
