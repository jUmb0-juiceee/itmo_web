package ru.itmo.students;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import ru.itmo.students.dto.RegistrationRequest;
import static org.assertj.core.api.Assertions.assertThat;

class RegistrationValidationTest {
    @Test
    void registrationChecksNameEmailAndPasswordWithoutExposingThePassword() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var valid = new RegistrationRequest(" student ", " student@example.org ", "test-password");
            assertThat(validator.validate(valid)).isEmpty();
            assertThat(valid.username()).isEqualTo("student");
            assertThat(valid.email()).isEqualTo("student@example.org");
            assertThat(valid.toString()).doesNotContain("test-password");
            var invalid = new RegistrationRequest("x", "not-an-email", "short");
            assertThat(validator.validate(invalid)).extracting(violation -> violation.getPropertyPath().toString())
                    .contains("username", "email", "password");
        }
    }

    @Test
    void bcryptLimitUsesUtf8BytesAndPasswordIsNotTrimmed() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new RegistrationRequest("student", "student@example.org", "Я".repeat(36)))).isEmpty();
            assertThat(validator.validate(new RegistrationRequest("student", "student@example.org", "Я".repeat(37))))
                    .extracting(violation -> violation.getPropertyPath().toString()).containsExactly("password");
            var password = "  abcdefgh  ";
            assertThat(new RegistrationRequest("student", "student@example.org", password).password()).isEqualTo(password);
        }
    }
}
