package ru.itmo.students.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.itmo.students.dto.ApiError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Security and servlet filters run outside MVC's @RestControllerAdvice. */
@Component
public class ApiErrorWriter {
    private final ObjectMapper json;
    public ApiErrorWriter(ObjectMapper json) { this.json = json; }

    public void write(HttpServletResponse response, int status, ApiError error) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getWriter(), error);
    }
}
