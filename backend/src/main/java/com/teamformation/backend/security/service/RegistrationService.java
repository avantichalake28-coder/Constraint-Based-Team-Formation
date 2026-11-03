package com.teamformation.backend.security.service;

import com.teamformation.backend.entity.EmailVerificationToken;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.repository.EmailVerificationTokenRepository;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.dto.RegisterRequest;
import com.teamformation.backend.security.dto.RegisterResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Public student registration. The user, student profile and verification token
 * are written in one transaction. The verification email is sent only after
 * that transaction commits; a mail failure keeps the account and is logged so
 * the student can use resend.
 */
@Service
public class RegistrationService {

    static final Pattern SRN_PATTERN = Pattern.compile("^[0-9]{2}FE[0-9]{2}[A-Z]{3}[0-9]{3}$");
    static final Duration TOKEN_TTL = Duration.ofHours(24);
    static final String DUPLICATE_MESSAGE = "An account with the provided details already exists";

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailDomainPolicy emailDomainPolicy;
    private final SecureTokenGenerator tokenGenerator;
    private final VerificationEmailDispatcher emailDispatcher;

    public RegistrationService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            EmailVerificationTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailDomainPolicy emailDomainPolicy,
            SecureTokenGenerator tokenGenerator,
            VerificationEmailDispatcher emailDispatcher) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailDomainPolicy = emailDomainPolicy;
        this.tokenGenerator = tokenGenerator;
        this.emailDispatcher = emailDispatcher;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String srn = normalizeSrn(request.srn());

        if (!emailDomainPolicy.isAllowed(email)) {
            throw new BusinessException("Email must belong to the " + emailDomainPolicy.getAllowedDomain() + " domain");
        }
        if (!SRN_PATTERN.matcher(srn).matches()) {
            throw new BusinessException(
                    "SRN must match format: 2 digits, FE, 2 digits, 3 uppercase letters, 3 digits (e.g. 02FE24BCS141)");
        }
        if (userRepository.existsByEmailIgnoreCase(email) || studentProfileRepository.existsBySrn(srn)) {
            throw new BusinessException(DUPLICATE_MESSAGE, HttpStatus.CONFLICT);
        }

        User user = new User(email, request.fullName().trim(), passwordEncoder.encode(request.password()), UserRole.STUDENT);
        user.setActive(false);

        try {
            user = userRepository.saveAndFlush(user);
            studentProfileRepository.saveAndFlush(new StudentProfile(user, srn, null, null, null));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(DUPLICATE_MESSAGE, HttpStatus.CONFLICT);
        }

        String rawToken = tokenGenerator.generate();
        EmailVerificationToken token = new EmailVerificationToken(
                user, TokenHasher.sha256Hex(rawToken), Instant.now().plus(TOKEN_TTL));
        tokenRepository.save(token);

        emailDispatcher.sendAfterCommit(user.getEmail(), user.getFullName(), rawToken);
        return new RegisterResponse(user.getId(), user.getEmail());
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeSrn(String srn) {
        return srn == null ? null : srn.trim().toUpperCase(Locale.ROOT);
    }
}
