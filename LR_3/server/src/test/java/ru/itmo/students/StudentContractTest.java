package ru.itmo.students;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import ru.itmo.students.dto.*;
import ru.itmo.students.entity.StudentEntity;
import ru.itmo.students.mapper.StudentMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class StudentContractTest {
    private final ObjectMapper json = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final StudentMapper mapper = new StudentMapper();

    @Test
    void responseRetainsLegacyFieldNamesAndDoesNotExposeDatabaseId() throws Exception {
        var entity = new StudentEntity(123456, "Иванов Иван Иванович", "M3301", 1, 101,
                LocalDate.of(2026, 9, 1), true, "Заметка");
        var node = json.readTree(json.writeValueAsString(mapper.toResponse(entity)));

        Set<String> fields = StreamSupport.stream(
                ((Iterable<String>) node::fieldNames).spliterator(), false).collect(Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder("isu", "fio", "group", "dorm", "room", "date", "isForeigner", "notes");
        assertThat(node.get("isForeigner").asBoolean()).isTrue();
        assertThat(node.get("date").asText()).isEqualTo("2026-09-01");
    }

    @Test
    void paginationRetainsOneBasedPageAndSnakeCaseFields() throws Exception {
        var response = new StudentPageResponse(List.of(), new PaginationResponse(1, 20, 0, 0, false, false));
        var node = json.readTree(json.writeValueAsString(response));
        assertThat(node.get("data").isArray()).isTrue();
        assertThat(node.at("/pagination/page").asInt()).isEqualTo(1);
        assertThat(node.at("/pagination/total_pages").asInt()).isZero();
        assertThat(node.at("/pagination/has_next").asBoolean()).isFalse();
        assertThat(node.at("/pagination/has_prev").asBoolean()).isFalse();
        assertThat(node.at("/pagination/totalPages").isMissingNode()).isTrue();
    }

    @Test
    void patchDistinguishesAnOmittedFieldFromExplicitNull() throws Exception {
        var patch = json.readValue("{\"dorm\":null,\"isForeigner\":false}", StudentPatchRequest.class);
        assertThat(patch.fields()).doesNotContainKey("fio");
        assertThat(patch.fields().get("dorm").isNull()).isTrue();
        assertThat(patch.fields().get("isForeigner").asBoolean()).isFalse();
        assertThat(json.readTree(json.writeValueAsString(patch)))
                .isEqualTo(json.readTree("{\"dorm\":null,\"isForeigner\":false}"));
    }

    @Test
    void legacyIsForeignerInputIsBoundToTheWriteDto() throws Exception {
        var request = json.readValue("""
                {"isu":123456,"fio":"Иванов Иван Иванович","group":"M3301","isForeigner":true}
                """, StudentWriteRequest.class);
        var response = mapper.toResponse(mapper.toEntity(request));
        assertThat(response.isForeigner()).isTrue();
        assertThat(response.notes()).isEmpty();
        assertThat(response.date()).isNull();
    }
}
