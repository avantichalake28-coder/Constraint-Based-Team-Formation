package com.teamformation.backend;

import com.teamformation.backend.repository.EmailVerificationTokenRepository;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.dto.RegisterRequest;
import com.teamformation.backend.security.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Proves the verification mail is dispatched only after a successful commit:
 * a rolled-back registration leaves no user, profile, token or message.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RegistrationRollbackIntegrationTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    private final List<SimpleMailMessage> sentMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        sentMessages.clear();
        doAnswer(invocation -> {
            sentMessages.add(invocation.getArgument(0));
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("A rolled-back registration writes nothing and sends no verification mail")
    void rollback_sendsNoMailAndPersistsNothing() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            registrationService.register(new RegisterRequest(
                    "Rollback Student",
                    "rollback.student@college.edu",
                    "SuperSecret123",
                    "02FE24BCS311"));
            status.setRollbackOnly();
        });

        assertThat(userRepository.count()).isZero();
        assertThat(studentProfileRepository.count()).isZero();
        assertThat(tokenRepository.count()).isZero();
        assertThat(sentMessages).isEmpty();
    }
}
