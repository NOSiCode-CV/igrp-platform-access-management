package cv.igrp.platform.access_management.shared.i18n;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string, when present, must be a platform language tag
 * ({@link IgrpLocale#fromTag}: {@code pt}, {@code en}, {@code fr} and their
 * regional/underscore/upper-case variants). {@code null} and blank are valid —
 * combine with {@code @NotBlank} to make the field mandatory.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SupportedLocale.Validator.class)
public @interface SupportedLocale {

    String message() default "{validation.locale.unsupported}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SupportedLocale, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || IgrpLocale.fromTag(value).isPresent();
        }
    }
}
