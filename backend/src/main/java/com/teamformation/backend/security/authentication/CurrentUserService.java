package com.teamformation.backend.security.authentication;

import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.dto.MeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the authenticated user from the JWT subject on each call and builds
 * the current-user DTO. The database remains the source of truth for role,
 * active state and the student SRN.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;

    public CurrentUserService(UserRepository userRepository, StudentProfileRepository studentProfileRepository) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    @Transactional(readOnly = true)
    public MeResponse getCurrentUser(Authentication authentication) {
        User user = requireCurrentUser(authentication);
        String srn = studentProfileRepository.findByUserId(user.getId())
                .map(profile -> profile.getSrn())
                .orElse(null);
        return new MeResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), srn);
    }

    @Transactional(readOnly = true)
    public User requireCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }

        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new BusinessException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Authentication is required", HttpStatus.UNAUTHORIZED));
        if (!user.isActive()) {
            throw new BusinessException("Account is not active", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    /**
     * Loads the authenticated user from the database and requires one of the
     * supplied stored roles. The role claim in the JWT is never trusted.
     */
    @Transactional(readOnly = true)
    public User requireCurrentUserWithRole(Authentication authentication, UserRole... allowedRoles) {
        User user = requireCurrentUser(authentication);
        for (UserRole role : allowedRoles) {
            if (user.getRole() == role) {
                return user;
            }
        }
        throw new BusinessException(
                "You do not have permission to access this resource", HttpStatus.FORBIDDEN);
    }
}
