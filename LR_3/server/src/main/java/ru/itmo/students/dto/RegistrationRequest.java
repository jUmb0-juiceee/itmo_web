package ru.itmo.students.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.itmo.students.validation.ValidPassword;

/** Registration data; the service always assigns USER regardless of any extra JSON fields. */
public record RegistrationRequest(
        @NotBlank(message = "Укажите имя пользователя.")
        @Size(min = 3, max = 64, message = "Имя пользователя должно содержать от 3 до 64 символов.")
        @Pattern(regexp = "[A-Za-z0-9_][A-Za-z0-9_.-]*", message = "В имени допустимы латинские буквы, цифры, _, . и -.")
        String username,
        @NotBlank(message = "Укажите email.")
        @Email(message = "Укажите корректный email.")
        @Size(max = 254, message = "Email не должен превышать 254 символа.") String email,
        @ValidPassword String password) {
    public RegistrationRequest {
        username = username == null ? null : username.strip();
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() { return "RegistrationRequest[password=<redacted>]"; }
}
