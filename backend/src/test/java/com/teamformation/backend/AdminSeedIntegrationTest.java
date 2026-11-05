package com.teamformation.backend;

import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.service.AdminSeedRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first-admin seed must run against the real database, create exactly one
 * active admin and do nothing on subsequent starts.
 */
@SpringBootTest(properties = {
        "ADMIN_EMAIL=seed.admin@college.edu",
        "ADMIN_PASSWORD=AdminSecret123"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AdminSeedIntegrationTest {

    @Autowired
    private AdminSeedRunner adminSeedRunner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Startup creates exactly one active admin with a hashed password and no student profile")
    void startup_createsExactlyOneAdmin() {
        List<User> admins = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .toList();

        assertThat(admins).hasSize(1);
        User admin = admins.getFirst();
        assertThat(admin.getEmail()).isEqualTo("seed.admin@college.edu");
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getPasswordHash()).isNotEqualTo("AdminSecret123");
        assertThat(passwordEncoder.matches("AdminSecret123", admin.getPasswordHash())).isTrue();
        assertThat(studentProfileRepository.findByUserId(admin.getId())).isEmpty();
    }

    @Test
    @DisplayName("Re-running the seeder does not create a second admin")
    void rerun_doesNotDuplicateAdmin() {
        long before = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .count();

        adminSeedRunner.run(new DefaultApplicationArguments(new String[0]));
        adminSeedRunner.run(new DefaultApplicationArguments(new String[0]));

        long after = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .count();
        assertThat(before).isEqualTo(1);
        assertThat(after).isEqualTo(1);
    }
}
