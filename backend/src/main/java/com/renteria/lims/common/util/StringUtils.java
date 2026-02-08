package com.renteria.lims.common.util;

public final class StringUtils {
    
    private StringUtils() {
        // Utility class
    }

    public static String maskEmail(String email) {
        if (email == null || email.length() < 5) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex < 2) return "***";
        return email.substring(0, 2) + "***@" + email.substring(atIndex + 1);
    }
}
