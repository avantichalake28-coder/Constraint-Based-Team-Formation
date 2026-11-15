package com.teamformation.backend.security.validation;

import com.teamformation.backend.security.service.EmailDomainPolicy;
import com.teamformation.backend.security.dto.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AllowedEmailDomainValidatorTest {

    private final AllowedEmailDomainValidator validator =
            new AllowedEmailDomainValidator(new EmailDomainPolicy("kletech.ac.in"));

    @Test
    void acceptsConfiguredDomainIgnoringCase() {
        assertThat(validator.isValid("Student@KLETECH.AC.IN", null)).isTrue();
    }

    @Test
    void rejectsOtherAndSuffixDomains() {
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addConstraintViolation()).thenReturn(context);

        assertThat(validator.isValid("student@gmail.com", context)).isFalse();
        assertThat(validator.isValid("student@kletech.ac.in.example.com", context)).isFalse();
        verify(context, times(2)).buildConstraintViolationWithTemplate(
                "Email must belong to the kletech.ac.in domain");
    }

    @Test
    void registrationRequestUsesTheConfiguredDomainConstraint() {
        try (ValidatorFactory defaultFactory = Validation.buildDefaultValidatorFactory()) {
            ConstraintValidatorFactory delegate = defaultFactory.getConstraintValidatorFactory();
            ConstraintValidatorFactory configuredFactory = new ConstraintValidatorFactory() {
                @Override
                public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
                    if (key == AllowedEmailDomainValidator.class) {
                        return key.cast(validator);
                    }
                    return delegate.getInstance(key);
                }

                @Override
                public void releaseInstance(ConstraintValidator<?, ?> instance) {
                    delegate.releaseInstance(instance);
                }
            };

            try (ValidatorFactory factory = Validation.byDefaultProvider()
                    .configure()
                    .constraintValidatorFactory(configuredFactory)
                    .buildValidatorFactory()) {
                Set<ConstraintViolation<RegisterRequest>> violations = factory.getValidator().validate(
                        new RegisterRequest("Student Example", "student@gmail.com", "SecurePassword123", "02FE24BCS141"));

                assertThat(violations)
                        .extracting(ConstraintViolation::getMessage)
                        .containsExactly("Email must belong to the kletech.ac.in domain");
            }
        }
    }
}
