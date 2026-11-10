package ru.itmo.students;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.itmo.students.controller.StudentController;
import ru.itmo.students.config.WebConfiguration;
import ru.itmo.students.exception.ApiExceptionHandler;
import ru.itmo.students.service.StudentService;
import ru.itmo.students.validation.StudentInputParser;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {
    @Test
    void racedDatabaseConflictsKeepTheirStatusAndHideSqlDetails() {
        var handler = new ApiExceptionHandler();
        var sql = new SQLException("SQL secret: uq_students_isu", "23505");
        var response = handler.database(new DataIntegrityViolationException("private SQL", sql));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().details()).containsKey("isu");
        assertThat(response.getBody().toString()).doesNotContain("SQL secret", "private SQL", "SQLException");
        var check = handler.database(new DataIntegrityViolationException("private data", new SQLException("private row", "23514")));
        assertThat(check.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unexpectedErrorsReturnGenericJsonWithoutExceptionOrStackTrace() throws Exception {
        var service = mock(StudentService.class);
        when(service.findByIsu(123456)).thenThrow(new IllegalStateException("internal-secret"));
        var parser = new StudentInputParser(JsonMapper.builder().build());
        var mvc = MockMvcBuilders.standaloneSetup(new StudentController(service, parser))
                .setCustomHandlerMapping(new WebConfiguration()::getRequestMappingHandlerMapping)
                .setControllerAdvice(new ApiExceptionHandler()).build();
        var response = mvc.perform(get("/api/requests/123456"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("Внутренняя ошибка сервера"))
                .andExpect(jsonPath("$.trace").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("internal-secret", "IllegalStateException", "stackTrace");
    }
}
