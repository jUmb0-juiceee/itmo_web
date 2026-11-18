package ru.itmo.students.validation;
import jakarta.validation.*;
import java.lang.annotation.*;
@Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy=CodePointSize.Check.class)
public @interface CodePointSize {
    String message() default "Maximum 500 Unicode characters.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    class Check implements ConstraintValidator<CodePointSize,String> {
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value==null || value.codePointCount(0,value.length())<=500;
        }
    }
}
