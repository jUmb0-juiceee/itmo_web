package ru.itmo.students;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.students.entity.StudentEntity;
import ru.itmo.students.repository.StudentRepository;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Transactional
@WithMockUser(roles = "ADMIN")
class StudentApiIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StudentRepository students;

    @Test
    void fullCrudCycleIncludesPutPatchIsuChangeAndDelete() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body(123456)))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/requests/123456"))
                .andExpect(jsonPath("$.isu").value(123456)).andExpect(jsonPath("$.id").doesNotExist());
        var id = students.findByIsu(123456).orElseThrow().getId();
        mvc.perform(get("/api/requests/123456")).andExpect(status().isOk());
        mvc.perform(put("/api/requests/123456").contentType(MediaType.APPLICATION_JSON).content(body(654321)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isu").value(654321));
        assertThat(students.findByIsu(654321).orElseThrow().getId()).isEqualTo(id);
        mvc.perform(get("/api/requests/123456")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/requests/654321").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Обновлено\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes").value("Обновлено"));
        mvc.perform(delete("/api/requests/654321")).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/requests/654321")).andExpect(status().isNotFound());
    }

    @Test
    void invalidPatchDoesNotPartiallyChangeAnExistingStudent() throws Exception {
        settledStudent();
        mvc.perform(patch("/api/requests/123456").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"room\":null,\"notes\":\"Не должно сохраниться\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.non_field_errors").exists());
        mvc.perform(get("/api/requests/123456")).andExpect(status().isOk())
                .andExpect(jsonPath("$.room").value(101)).andExpect(jsonPath("$.notes").value("Исходная заметка"));
    }

    @Test
    void patchOmissionsKeepValuesAndExplicitNullClearsSettlement() throws Exception {
        settledStudent();
        mvc.perform(patch("/api/requests/123456").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Новая\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dorm").value(1)).andExpect(jsonPath("$.isForeigner").value(true));
        mvc.perform(patch("/api/requests/123456").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dorm\":null,\"room\":null,\"date\":null,\"isForeigner\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dorm").isEmpty())
                .andExpect(jsonPath("$.room").isEmpty()).andExpect(jsonPath("$.date").isEmpty())
                .andExpect(jsonPath("$.isForeigner").value(false)).andExpect(jsonPath("$.notes").value("Новая"));
    }

    @Test
    void putResetsOmittedOptionalFieldsWhilePatchIgnoresUnknownFields() throws Exception {
        settledStudent();
        mvc.perform(patch("/api/requests/123456").contentType(MediaType.APPLICATION_JSON).content("{\"unknown\":123}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.room").value(101));
        mvc.perform(put("/api/requests/123456").contentType(MediaType.APPLICATION_JSON).content(body(123456)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.room").isEmpty())
                .andExpect(jsonPath("$.isForeigner").value(false)).andExpect(jsonPath("$.notes").value(""));
    }

    @Test
    void duplicatesOnCreateAndIsuChangeAreConflicts() throws Exception {
        settledStudent();
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body(123456)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details.isu").exists());
        students.saveAndFlush(new StudentEntity(654321, "Петров Пётр Петрович", "M3301", null, null, null, false, ""));
        mvc.perform(patch("/api/requests/123456").contentType(MediaType.APPLICATION_JSON).content("{\"isu\":654321}"))
                .andExpect(status().isConflict());
        assertThat(students.findByIsu(123456)).isPresent();
    }

    @Test
    void filteredSearchIsCaseInsensitiveForCyrillicAndCombinesWithFalseBoolean() throws Exception {
        listFixtures();
        mvc.perform(get("/api/requests").param("search", "  ИВАНОВ  ").param("group", "M3301")
                        .param("dorm", "1").param("room", "101").param("isForeigner", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.total").value(1))
                .andExpect(jsonPath("$.data[0].isu").value(333333));
        mvc.perform(get("/api/requests").param("search", "ОСОБАЯ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.total").value(1))
                .andExpect(jsonPath("$.data[0].isu").value(222222));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\"})
    void searchTreatsSqlWildcardsAsLiteralCharacters(String search) throws Exception {
        listFixtures();
        mvc.perform(get("/api/requests").param("search", search))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.total").value(1));
    }

    @Test
    void sortingAndPaginationAreAppliedInDatabaseWithStableTies() throws Exception {
        listFixtures();
        mvc.perform(get("/api/requests").param("sort", "isu,asc").param("limit", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].isu").value(222222))
                .andExpect(jsonPath("$.pagination.total").value(3)).andExpect(jsonPath("$.pagination.total_pages").value(3))
                .andExpect(jsonPath("$.pagination.has_prev").value(true)).andExpect(jsonPath("$.pagination.has_next").value(true));
        mvc.perform(get("/api/requests").param("sort", "isu,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].isu").value(333333));
        mvc.perform(get("/api/requests").param("sort", "group,asc").param("limit", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].isu").value(111111));
    }

    @Test
    void queryAndGetReturnTheSamePage() throws Exception {
        listFixtures();
        String get = mvc.perform(get("/api/requests").param("isForeigner", "false").param("sort", "isu,desc").param("limit", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String query = mvc.perform(request(HttpMethod.valueOf("QUERY"), "/api/requests")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isForeigner\":false,\"sort\":\"isu,desc\",\"limit\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(query)).isEqualTo(json.readTree(get));
    }

    @Test
    void paginationFallbackAndLargePageKeepTheLegacyContract() throws Exception {
        listFixtures();
        mvc.perform(get("/api/requests").param("page", "2").param("limit", "oops").param("unknown", "ignored"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(20));
        mvc.perform(get("/api/requests").param("page", "0").param("limit", "101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(20));
        mvc.perform(get("/api/requests").param("page", "2147483647").param("limit", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.pagination.total").value(3)).andExpect(jsonPath("$.pagination.has_next").value(false));
    }

    @Test
    void emptyListHasZeroPages() throws Exception {
        mvc.perform(get("/api/requests"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.pagination.total_pages").value(0));
    }

    @ParameterizedTest
    @MethodSource("invalidStudentFields")
    void invalidFieldValuesAre422WithFieldErrors(String field, String value) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(body(123456));
        node.set(field, json.readTree(value));
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(node)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.details." + field).isString());
        assertThat(students.count()).isZero();
    }

    static Stream<Arguments> invalidStudentFields() {
        return Stream.of(
                Arguments.of("isu", "12345"), Arguments.of("isu", "123456.5"),
                Arguments.of("fio", "\"John Smith\""), Arguments.of("fio", "\"" + "А".repeat(151) + "\""),
                Arguments.of("group", "\"m3301\""), Arguments.of("dorm", "true"), Arguments.of("dorm", "0"),
                Arguments.of("room", "10000"), Arguments.of("date", "\"2026-02-30\""),
                Arguments.of("date", "\"1999-12-31\""), Arguments.of("isForeigner", "\"maybe\""),
                Arguments.of("notes", "\"" + "я".repeat(501) + "\""));
    }

    @Test
    void missingRequiredFieldsAndMalformedJsonAre400() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.isu").exists());
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "true", "\"text\""})
    void bodyMustBeAnObject(String body) throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        mvc.perform(request(HttpMethod.valueOf("QUERY"), "/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
    }

    @Test
    void numericStringsAndTextFalseAreNormalizedWithoutTruthinessCoercion() throws Exception {
        String body = """
                {"isu":"123456","fio":" Иванов Иван Иванович ","group":" M3301 ",
                 "dorm":"","room":"","date":"","isForeigner":"false","notes":"  Заметка  "}
                """;
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isForeigner").value(false))
                .andExpect(jsonPath("$.fio").value("Иванов Иван Иванович"))
                .andExpect(jsonPath("$.notes").value("Заметка")).andExpect(jsonPath("$.date").isEmpty());
    }

    @Test
    void notesLimitCountsUnicodeCharactersLikePostgresRatherThanUtf16CodeUnits() throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(body(123456));
        node.put("notes", "😀".repeat(500));
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(node)))
                .andExpect(status().isCreated());
        node.put("isu", 654321);
        node.put("notes", "😀".repeat(501));
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(node)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.notes").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"group=bad", "dorm=1.5", "room=true", "isForeigner=maybe", "sort=fio;DROP TABLE students,asc", "sort=isu,sideways"})
    void invalidFiltersAndSortAre400(String parameter) throws Exception {
        String[] pair = parameter.split("=", 2);
        mvc.perform(get("/api/requests").param(pair[0], pair[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details." + pair[0]).exists());
    }

    @Test
    void malformedPatchRequiredNullAndUnknownStudentAreHandled() throws Exception {
        settledStudent();
        mvc.perform(patch("/api/requests/123456").contentType(MediaType.APPLICATION_JSON).content("{\"isu\":null}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.isu").exists());
        mvc.perform(patch("/api/requests/123456").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/requests/999999").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/requests/999999").contentType(MediaType.APPLICATION_JSON).content(body(999999)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/requests/999999")).andExpect(status().isNotFound());
    }

    @Test
    void methodAndMediaTypeErrorsKeepJsonFormat() throws Exception {
        mvc.perform(delete("/api/requests")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("QUERY"))).andExpect(jsonPath("$.error").isString());
        mvc.perform(post("/api/requests/123456").contentType(MediaType.APPLICATION_JSON).content(body(123456)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/requests").contentType(MediaType.TEXT_PLAIN).content(body(123456)))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.error").isString());
        mvc.perform(get("/api/requests").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable()).andExpect(jsonPath("$.error").isString());
        mvc.perform(get("/api/requests/not-a-number")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void readRoleCanUseQueryButCannotWrite() throws Exception {
        mvc.perform(get("/api/requests")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.valueOf("QUERY"), "/api/requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body(123456)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("Недостаточно прав"));
    }

    @Test
    @WithAnonymousUser
    void anonymousDataAccessIsDeniedButOptionsIsAvailable() throws Exception {
        mvc.perform(get("/api/requests")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").isString());
        mvc.perform(request(HttpMethod.valueOf("QUERY"), "/api/requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(options("/api/requests")).andExpect(status().isNoContent())
                .andExpect(header().string("Allow", containsString("QUERY")));
    }

    private void settledStudent() {
        students.saveAndFlush(new StudentEntity(123456, "Иванов Иван Иванович", "M3301", 1, 101,
                LocalDate.of(2026, 9, 1), true, "Исходная заметка"));
    }

    private void listFixtures() {
        students.saveAndFlush(new StudentEntity(333333, "Иванов Иван Иванович", "M3301", 1, 101,
                LocalDate.of(2026, 9, 1), false, "Символ %"));
        students.saveAndFlush(new StudentEntity(111111, "Петров Пётр Петрович", "M3301", null, null, null, true, "Символ _"));
        students.saveAndFlush(new StudentEntity(222222, "Сидоров Сергей Сергеевич", "M3302", null, null, null, false, "Особая заметка \\"));
    }

    private static String body(int isu) {
        return "{\"isu\":" + isu + ",\"fio\":\"Иванов Иван Иванович\",\"group\":\"M3301\"}";
    }
}
