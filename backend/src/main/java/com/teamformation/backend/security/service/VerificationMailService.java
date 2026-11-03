package com.teamformation.backend.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the email verification link through Spring's mail support. SMTP
 * coordinates come from environment variables through {@code spring.mail.*};
 * the visible sender and base URL come from {@code MAIL_FROM} and
 * {@code APP_BASE_URL}.
 */
@Service
public class VerificationMailService {

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String appBaseUrl;

    public VerificationMailService(
            JavaMailSender mailSender,
            @Value("${MAIL_FROM:no-reply@localhost}") String mailFrom,
            @Value("${APP_BASE_URL:http://localhost:8081}") String appBaseUrl) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
    }

    public void sendVerificationEmail(String recipient, String fullName, String rawToken) {
        String link = appBaseUrl + "/api/v1/auth/verify-email?token=" + rawToken;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(recipient);
        message.setSubject("Verify your email address");
        message.setText("Hello " + fullName + ",\n\n"
                + "Verify your student account by opening this link:\n"
                + link + "\n\n"
                + "This link is valid for 24 hours and can be used once.\n");
        mailSender.send(message);
    }
}
