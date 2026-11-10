package ru.itmo.students.exception;

import org.springframework.http.HttpStatus;
import ru.itmo.students.dto.ApiError;
import java.util.Map;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final ApiError error;

    public ApiException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    public ApiException(HttpStatus status, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.error = new ApiError(message, details);
    }

    public HttpStatus status() { return status; }
    public ApiError error() { return error; }
}
