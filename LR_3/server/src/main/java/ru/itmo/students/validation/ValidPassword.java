package ru.itmo.students.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
public @interface ValidPassword {
    String message() default "Пароль должен содержать минимум 8 символов и не превышать 72 байта UTF-8.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
