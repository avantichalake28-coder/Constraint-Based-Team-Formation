package com.teamformation.backend.security.authentication;

import com.teamformation.backend.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Issues 60-minute HMAC-SHA256 access tokens. Claims are subject = user id and
 * role = the user's stored role. There is no refresh token and no revocation
 * list; the database is consulted on every request so deactivation and role
 * changes take effect immediately.
 */
@Service
public class JwtService {

    public static final long ACCESS_TOKEN_SECONDS = 3600;

    private final JwtEncoder jwtEncoder;

    public JwtService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String generateAccessToken(User user) {
        Instant issuedAt = Instant.now();
        return generateAccessToken(user, issuedAt, issuedAt.plusSeconds(ACCESS_TOKEN_SECONDS));
    }

    public String generateAccessToken(User user, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
