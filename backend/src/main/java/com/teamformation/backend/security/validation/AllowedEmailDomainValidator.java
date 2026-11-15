package com.teamformation.backend.security.validation;

import com.teamformation.backend.security.dto.AllowedEmailDomain;
import com.teamformation.backend.security.service.EmailDomainPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AllowedEmailDomainValidator implements ConstraintValidator<AllowedEmailDomain, String> {

    private final EmailDomainPolicy emailDomainPolicy;

    public AllowedEmailDomainValidator(EmailDomainPolicy emailDomainPolicy) {
        this.emailDomainPolicy = emailDomainPolicy;
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.isBlank()) {
            return true;
        }
        if (emailDomainPolicy.isAllowed(email)) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "Email must belong to the " + emailDomainPolicy.getAllowedDomain() + " domain")
                .addConstraintViolation();
        return false;
    }
}
