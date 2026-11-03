package com.teamformation.backend.security.service;

import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.dto.CreateMentorRequest;
import com.teamformation.backend.security.dto.MentorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only mentor account creation. Mentors are active immediately and do
 * not have a student profile or SRN.
 */
@Service
public class MentorAccountService {

    static final String DUPLICATE_MESSAGE = "An account with the provided email already exists";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public MentorAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public MentorResponse createMentor(CreateMentorRequest request) {
        String email = RegistrationService.normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(DUPLICATE_MESSAGE, HttpStatus.CONFLICT);
        }

        User mentor = new User(
                email,
                request.fullName().trim(),
                passwordEncoder.encode(request.initialPassword()),
                UserRole.MENTOR);
        mentor.setActive(true);

        try {
            mentor = userRepository.saveAndFlush(mentor);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(DUPLICATE_MESSAGE, HttpStatus.CONFLICT);
        }

        return new MentorResponse(mentor.getId(), mentor.getFullName(), mentor.getEmail(), mentor.getRole());
    }
}
