package com.teamformation.backend.security.service;

import com.teamformation.backend.entity.User;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.authentication.JwtService;
import com.teamformation.backend.security.dto.LoginRequest;
import com.teamformation.backend.security.dto.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email/password login with a generic credential error and an in-memory
 * failure throttle. Only active (verified) accounts receive a token.
 */
@Service
public class AuthenticationService {

    static final String GENERIC_CREDENTIAL_MESSAGE = "Invalid email or password";
    static final String UNVERIFIED_MESSAGE = "Please verify your email address before logging in";
    static final String THROTTLED_MESSAGE = "Too many failed login attempts. Try again later";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = RegistrationService.normalizeEmail(request.email());

        if (loginAttemptService.isBlocked(email)) {
            throw new BusinessException(THROTTLED_MESSAGE, HttpStatus.TOO_MANY_REQUESTS);
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(email);
            throw new BusinessException(GENERIC_CREDENTIAL_MESSAGE, HttpStatus.UNAUTHORIZED);
        }

        if (!user.isActive()) {
            throw new BusinessException(UNVERIFIED_MESSAGE, HttpStatus.UNAUTHORIZED);
        }

        loginAttemptService.reset(email);
        String token = jwtService.generateAccessToken(user);
        return new LoginResponse(token, "Bearer", JwtService.ACCESS_TOKEN_SECONDS);
    }
}
