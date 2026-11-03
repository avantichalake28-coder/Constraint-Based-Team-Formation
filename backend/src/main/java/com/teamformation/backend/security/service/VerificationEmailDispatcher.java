package com.teamformation.backend.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends verification mail only after the surrounding transaction commits.
 *
 * <p>A mail failure is logged without the raw token and does not roll back an
 * account: the student can request another link through resend. When no
 * transaction is active the mail is sent immediately.</p>
 */
@Service
public class VerificationEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(VerificationEmailDispatcher.class);

    private final VerificationMailService mailService;

    public VerificationEmailDispatcher(VerificationMailService mailService) {
        this.mailService = mailService;
    }

    public void sendAfterCommit(String recipient, String fullName, String rawToken) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trySend(recipient, fullName, rawToken);
                }
            });
            return;
        }
        trySend(recipient, fullName, rawToken);
    }

    private void trySend(String recipient, String fullName, String rawToken) {
        try {
            mailService.sendVerificationEmail(recipient, fullName, rawToken);
        } catch (RuntimeException ex) {
            // Never log the raw token. The account and token stay committed so
            // a later resend request can replace them.
            log.error("Failed to send verification email to {}: {}", recipient, ex.getMessage());
        }
    }
}
