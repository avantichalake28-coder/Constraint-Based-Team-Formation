package com.teamformation.backend.repository;

import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User}.
 * Supports authentication lookups by normalized email (FR-01).
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Use case: load a user during authentication by their normalized email.
     * The database collation is case-insensitive, so ignoring case keeps the
     * lookup consistent regardless of how the address was typed.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Use case: registration must reject an email that already exists.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Use case: the startup seeder only creates the first admin when no admin
     * account exists yet.
     */
    boolean existsByRole(UserRole role);
}
