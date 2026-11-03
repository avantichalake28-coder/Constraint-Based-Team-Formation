package com.teamformation.backend.repository;

import com.teamformation.backend.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link EmailVerificationToken}.
 * Tokens are looked up by their stored SHA-256 hash only.
 */
@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    /**
     * Use case: verification loads the token together with its owner so the
     * account can be activated in the same transaction.
     */
    @EntityGraph(attributePaths = "user")
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Use case: resend invalidates every still-usable token before issuing one
     * new link. Only the unused rows for one account are needed.
     */
    List<EmailVerificationToken> findByUserIdAndUsedAtIsNull(Long userId);
}
