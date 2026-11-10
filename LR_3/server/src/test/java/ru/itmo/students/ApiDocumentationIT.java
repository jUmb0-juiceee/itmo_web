package ru.itmo.students;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ApiDocumentationIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void documentationIsPublicButStudentDataStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andExpect(content().string(containsString("bearerAuth")));
        mvc.perform(get("/api/requests")).andExpect(status().isUnauthorized());
    }

    @Test
    void contractHasAccurateMethodsSchemasAndTheQueryExtension() throws Exception {
        JsonNode api = contract();
        assertThat(api.get("openapi").asText()).startsWith("3.0.");
        assertThat(names(api.at("/paths"))).containsExactlyInAnyOrder(
                "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/me",
                "/api/requests", "/api/requests/{isu}", "/actuator/health");
        var collection = api.at("/paths").get("/api/requests");
        assertThat(names(collection)).containsExactlyInAnyOrder("get", "post", "options", "x-query");
        assertThat(collection.at("/x-query/operationId").asText()).isEqualTo("queryStudents");
        assertThat(collection.at("/post/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/StudentWriteRequest");
        var detail = api.at("/paths").get("/api/requests/{isu}");
        assertThat(names(detail)).containsExactlyInAnyOrder("parameters", "get", "put", "patch", "delete");
        assertThat(detail.at("/patch/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/StudentPatchRequest");
        assertThat(api.at("/components/schemas/StudentPatchRequest/required").isMissingNode()).isTrue();
        var fields = new HashSet<String>();
        collection.at("/get/parameters").forEach(parameter -> fields.add(parameter.get("$ref").asText()));
        assertThat(fields).hasSize(8);
        assertThat(names(api.at("/components/schemas"))).doesNotContain("UserEntity", "RefreshTokenEntity", "Jwt");
        assertThat(api.toString()).doesNotContain("passwordHash", "token_hash", "password_hash");
        assertThat(api.at("/components/schemas/PaginationResponse/properties/total_pages").isObject()).isTrue();
        assertThat(api.at("/components/schemas/StudentQueryRequest").isObject()).isTrue();
        assertLocalReferencesResolve(api, api);
    }

    @Test
    void publicAuthDoesNotInheritBearerAndProtectedMethodsDo() throws Exception {
        var api = contract();
        assertThat(api.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
        assertThat(api.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(api.at("/security/0/bearerAuth").isArray()).isTrue();
        for (String endpoint : List.of("register", "login", "refresh", "logout")) {
            assertThat(api.get("paths").get("/api/auth/" + endpoint).at("/post/security").isArray()).isTrue();
            assertThat(api.get("paths").get("/api/auth/" + endpoint).at("/post/security").size()).isZero();
        }
        assertThat(api.get("paths").get("/api/auth/me").at("/get/security").isMissingNode()).isTrue();
        assertThat(api.at("/components/schemas/RegistrationRequest/properties").has("role")).isFalse();
        assertThat(api.at("/components/schemas/RegistrationRequest/properties/password/writeOnly").asBoolean()).isTrue();
    }

    private JsonNode contract() throws Exception {
        return json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
    private Set<String> names(JsonNode object) {
        var names = new HashSet<String>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private void assertLocalReferencesResolve(JsonNode node, JsonNode api) {
        if (node.isObject() && node.has("$ref") && node.get("$ref").asText().startsWith("#/")) {
            String reference = node.get("$ref").asText();
            assertThat(api.at(reference.substring(1)).isMissingNode()).as("Reference %s must resolve", reference).isFalse();
        }
        node.elements().forEachRemaining(child -> assertLocalReferencesResolve(child, api));
    }
}
