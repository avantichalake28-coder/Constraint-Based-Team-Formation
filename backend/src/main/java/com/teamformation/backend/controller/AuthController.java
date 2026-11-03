package com.teamformation.backend.controller;

import com.teamformation.backend.common.ApiResponse;
import com.teamformation.backend.security.dto.LoginRequest;
import com.teamformation.backend.security.dto.LoginResponse;
import com.teamformation.backend.security.dto.MeResponse;
import com.teamformation.backend.security.dto.RegisterRequest;
import com.teamformation.backend.security.dto.RegisterResponse;
import com.teamformation.backend.security.authentication.CurrentUserService;
import com.teamformation.backend.security.dto.ResendVerificationRequest;
import com.teamformation.backend.security.dto.VerifyEmailRequest;
import com.teamformation.backend.security.service.AuthenticationService;
import com.teamformation.backend.security.service.EmailVerificationService;
import com.teamformation.backend.security.service.RegistrationService;
import com.teamformation.backend.security.service.VerificationResendService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints: student registration, email verification,
 * login and the current-user view.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final AuthenticationService authenticationService;
    private final CurrentUserService currentUserService;
    private final VerificationResendService verificationResendService;

    public AuthController(
            RegistrationService registrationService,
            EmailVerificationService emailVerificationService,
            AuthenticationService authenticationService,
            CurrentUserService currentUserService,
            VerificationResendService verificationResendService) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.authenticationService = authenticationService;
        this.currentUserService = currentUserService;
        this.verificationResendService = verificationResendService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Registration successful. Please verify your email address"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam(required = false) String token) {
        emailVerificationService.verify(token);
        return ResponseEntity.ok(ApiResponse.success(null, "Email verified successfully"));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmailPost(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok(ApiResponse.success(null, "Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        verificationResendService.resend(request);
        return ResponseEntity.ok(ApiResponse.success(null, VerificationResendService.GENERIC_MESSAGE));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authenticationService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(currentUserService.getCurrentUser(authentication)));
    }
}
