package com.teamformation.backend.security.authentication;

import com.teamformation.backend.entity.User;
import com.teamformation.backend.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the JWT subject (user id) against the database on every request.
 * The stored role and active flag are authoritative, so a deactivated account
 * or a changed role takes effect without waiting for the token to expire.
 */
@Component
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

    private final UserRepository userRepository;

    public DatabaseJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Invalid token subject");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid token subject"));
        if (!user.isActive()) {
            throw new BadCredentialsException("User account is not active");
        }

        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, user.getEmail());
    }
}
