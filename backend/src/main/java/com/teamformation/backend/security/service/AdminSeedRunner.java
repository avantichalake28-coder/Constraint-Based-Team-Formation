package com.teamformation.backend.security.service;

import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates the first ADMIN on startup when no admin exists and both
 * {@code ADMIN_EMAIL} and {@code ADMIN_PASSWORD} are configured. There is no
 * default password. If the variables are absent, a warning is logged and the
 * application continues.
 */
@Component
public class AdminSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);
    private static final int MIN_PASSWORD_LENGTH = 10;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeedRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_EMAIL:}") String adminEmail,
            @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("No ADMIN account exists and ADMIN_EMAIL/ADMIN_PASSWORD are not both set; "
                    + "admin login is unavailable until an admin is created.");
            return;
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least 10 characters");
        }
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            log.warn("No ADMIN account exists but ADMIN_EMAIL is already used by a non-admin account; "
                    + "set a different ADMIN_EMAIL or promote that account manually.");
            return;
        }

        String email = adminEmail.toLowerCase(Locale.ROOT);
        User admin = new User(email, "Administrator", passwordEncoder.encode(adminPassword), UserRole.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);
        log.info("Created the initial ADMIN account for {}", email);
    }
}
