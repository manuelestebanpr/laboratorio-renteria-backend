package com.renteria.lims.email.model;

public enum EmailTemplate {
    INITIAL_PASSWORD("initial-password", "Bienvenido a Laboratorio Renteria - Tu contraseña temporal"),
    PASSWORD_RESET("password-reset", "Restablecer tu contraseña - Laboratorio Renteria"),
    ACCOUNT_LOCKOUT("account-lockout", "Cuenta bloqueada por seguridad - Laboratorio Renteria");

    private final String templateName;
    private final String subject;

    EmailTemplate(String templateName, String subject) {
        this.templateName = templateName;
        this.subject = subject;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getSubject() {
        return subject;
    }
}
