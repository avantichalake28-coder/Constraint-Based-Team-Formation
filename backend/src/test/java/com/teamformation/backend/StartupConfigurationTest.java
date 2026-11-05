package com.teamformation.backend;

import com.teamformation.backend.security.config.JwtConfig;
import com.teamformation.backend.security.service.EmailDomainPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Startup must fail fast when the two mandatory security settings are missing
 * or unusable. These tests do not need a database because only the individual
 * configuration classes are loaded.
 */
class StartupConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    @DisplayName("A blank ALLOWED_EMAIL_DOMAIN fails startup")
    void blankAllowedDomain_failsStartup() {
        contextRunner
                .withUserConfiguration(EmailDomainPolicy.class)
                .withPropertyValues("ALLOWED_EMAIL_DOMAIN=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("ALLOWED_EMAIL_DOMAIN environment variable must be set to the college email domain");
                });
    }

    @Test
    @DisplayName("An ALLOWED_EMAIL_DOMAIN without a dot fails startup")
    void malformedAllowedDomain_failsStartup() {
        contextRunner
                .withUserConfiguration(EmailDomainPolicy.class)
                .withPropertyValues("ALLOWED_EMAIL_DOMAIN=college")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("ALLOWED_EMAIL_DOMAIN must be a domain such as college.edu");
                });
    }

    @Test
    @DisplayName("A configured ALLOWED_EMAIL_DOMAIN normalises and loads")
    void validAllowedDomain_loads() {
        contextRunner
                .withUserConfiguration(EmailDomainPolicy.class)
                .withPropertyValues("ALLOWED_EMAIL_DOMAIN=@College.EDU")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmailDomainPolicy.class).getAllowedDomain())
                            .isEqualTo("college.edu");
                });
    }

    @Test
    @DisplayName("A missing JWT_SECRET fails startup")
    void missingJwtSecret_failsStartup() {
        contextRunner
                .withUserConfiguration(JwtConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("JWT_SECRET environment variable must be set (at least 32 bytes)");
                });
    }

    @Test
    @DisplayName("A JWT_SECRET shorter than 32 bytes fails startup")
    void shortJwtSecret_failsStartup() {
        contextRunner
                .withUserConfiguration(JwtConfig.class)
                .withPropertyValues("JWT_SECRET=too-short")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("JWT_SECRET must be at least 32 bytes");
                });
    }

    @Test
    @DisplayName("A 32-byte JWT_SECRET loads the encoder and decoder")
    void validJwtSecret_loads() {
        contextRunner
                .withUserConfiguration(JwtConfig.class)
                .withPropertyValues("JWT_SECRET=0123456789abcdef0123456789abcdef")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtConfig.class);
                });
    }
}
