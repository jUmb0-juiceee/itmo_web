package ru.itmo.students.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.itmo.students.dto.ApiError;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> application(ApiException exception) {
        return ResponseEntity.status(exception.status()).contentType(MediaType.APPLICATION_JSON).body(exception.error());
    }

    @ExceptionHandler({DataAccessException.class, TransactionSystemException.class})
    public ResponseEntity<ApiError> database(RuntimeException exception) {
        SQLException sql = sqlCause(exception);
        if (sql != null) {
            String state = sql.getSQLState();
            if ("23505".equals(state)) {
                String message = sql.getMessage() == null ? "" : sql.getMessage();
                String field = message.contains("uq_students_isu") ? "isu"
                        : message.contains("uq_users_username") ? "username"
                        : message.contains("uq_users_email") ? "email" : null;
                return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(new ApiError("Конфликт уникальности данных",
                        field == null ? Map.of() : Map.of(field, "Запись с таким значением уже существует.")));
            }
            if ("23503".equals(state)) return error(HttpStatus.CONFLICT, "Операция нарушает связь между данными");
            if (state != null && (state.startsWith("22") || state.equals("23502") || state.equals("23514"))) {
                return error(HttpStatus.UNPROCESSABLE_ENTITY, "Данные не соответствуют ограничениям модели");
            }
        }
        log.error("Database operation failed", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraint(ConstraintViolationException exception) {
        var details = new LinkedHashMap<String, Object>();
        exception.getConstraintViolations().forEach(violation ->
                details.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return ResponseEntity.unprocessableEntity().contentType(MediaType.APPLICATION_JSON).body(new ApiError("Ошибка валидации", details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, "Недостаточно прав");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> unauthenticated(AuthenticationException exception) {
        return error(HttpStatus.UNAUTHORIZED, "Требуется авторизация");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = switch (status.value()) {
            case 400 -> "Некорректный JSON или параметры запроса";
            case 404 -> "Ресурс не найден";
            case 405 -> "HTTP-метод не поддерживается для этого ресурса";
            case 406 -> "Запрошенный формат ответа не поддерживается";
            case 415 -> "Неподдерживаемый тип содержимого. Используйте application/json";
            default -> status.is5xxServerError() ? "Внутренняя ошибка сервера" : "Ошибка запроса";
        };
        var details = new LinkedHashMap<String, Object>();
        if (exception instanceof MethodArgumentNotValidException invalid) {
            message = "Ошибка валидации";
            status = HttpStatus.UNPROCESSABLE_ENTITY;
            invalid.getBindingResult().getFieldErrors().forEach(error ->
                    details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        }
        if (status.is5xxServerError()) log.error("HTTP request failed", exception);
        // Discard framework ProblemDetail bodies so every API error keeps the same contract.
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON).body(new ApiError(message, details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ApiError(message, Map.of()));
    }

    private SQLException sqlCause(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof SQLException sql) return sql;
        }
        return null;
    }
}
