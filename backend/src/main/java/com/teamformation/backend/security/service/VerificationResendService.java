package com.teamformation.backend.security.service;

import com.teamformation.backend.entity.EmailVerificationToken;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.repository.EmailVerificationTokenRepository;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.dto.ResendVerificationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Public resend of the verification link. The response is deliberately the
 * same for unknown, already-verified and unverified addresses. Only an
 * existing unverified student receives a new token and mail.
 */
@Service
public class VerificationResendService {

    public static final String GENERIC_MESSAGE =
            "If the address belongs to an unverified student account, a verification email will be sent";
    static final String THROTTLED_MESSAGE = "Too many verification email requests. Try again later";

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final SecureTokenGenerator tokenGenerator;
    private final VerificationEmailDispatcher emailDispatcher;
    private final VerificationResendRateLimiter rateLimiter;

    public VerificationResendService(
            UserRepository userRepository,
            EmailVerificationTokenRepository tokenRepository,
            SecureTokenGenerator tokenGenerator,
            VerificationEmailDispatcher emailDispatcher,
            VerificationResendRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.emailDispatcher = emailDispatcher;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public void resend(ResendVerificationRequest request) {
        String email = RegistrationService.normalizeEmail(request.email());
        if (!rateLimiter.tryAcquire(email)) {
            throw new BusinessException(THROTTLED_MESSAGE, HttpStatus.TOO_MANY_REQUESTS);
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || user.getRole() != UserRole.STUDENT || user.isActive()) {
            return;
        }

        Instant now = Instant.now();
        List<EmailVerificationToken> activeTokens =
                tokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
        for (EmailVerificationToken token : activeTokens) {
            token.markUsed(now);
        }
        tokenRepository.saveAll(activeTokens);

        String rawToken = tokenGenerator.generate();
        tokenRepository.save(new EmailVerificationToken(
                user, TokenHasher.sha256Hex(rawToken), now.plus(RegistrationService.TOKEN_TTL)));
        emailDispatcher.sendAfterCommit(user.getEmail(), user.getFullName(), rawToken);
    }
}
