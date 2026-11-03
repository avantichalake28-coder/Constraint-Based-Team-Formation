package com.teamformation.backend.security.service;

import com.teamformation.backend.entity.EmailVerificationToken;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.repository.EmailVerificationTokenRepository;
import com.teamformation.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Consumes a verification token and activates the student account.
 * Unknown, expired and already-used tokens all produce the same generic 400.
 */
@Service
public class EmailVerificationService {

    static final String INVALID_TOKEN_MESSAGE = "Verification link is invalid or has expired";

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(INVALID_TOKEN_MESSAGE);
        }

        String tokenHash = TokenHasher.sha256Hex(rawToken);
        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(INVALID_TOKEN_MESSAGE));

        Instant now = Instant.now();
        if (token.isUsed() || token.isExpired(now)) {
            throw new BusinessException(INVALID_TOKEN_MESSAGE);
        }

        token.markUsed(now);
        User user = token.getUser();
        user.setActive(true);
        user.setUpdatedAt(now);
        userRepository.save(user);
        tokenRepository.save(token);
    }
}
