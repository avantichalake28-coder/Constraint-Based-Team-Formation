package com.teamformation.backend.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Enforces the single college email domain supplied by the environment.
 * Missing or blank configuration fails application startup.
 */
@Component
public class EmailDomainPolicy {

    private final String allowedDomain;

    public EmailDomainPolicy(@Value("${ALLOWED_EMAIL_DOMAIN:}") String allowedDomain) {
        if (allowedDomain == null || allowedDomain.isBlank()) {
            throw new IllegalStateException(
                    "ALLOWED_EMAIL_DOMAIN environment variable must be set to the college email domain");
        }
        String normalized = allowedDomain.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || !normalized.contains(".")) {
            throw new IllegalStateException(
                    "ALLOWED_EMAIL_DOMAIN must be a domain such as college.edu");
        }
        this.allowedDomain = normalized;
    }

    public boolean isAllowed(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        return domain.equals(allowedDomain);
    }

    public String getAllowedDomain() {
        return allowedDomain;
    }
}
