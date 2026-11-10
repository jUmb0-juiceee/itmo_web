package ru.itmo.students;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import ru.itmo.students.repository.StudentRepository;
import ru.itmo.students.repository.UserRepository;
import ru.itmo.students.entity.UserEntity;
import ru.itmo.students.entity.Role;
import java.time.Instant;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises Tomcat, real login/access JWTs and committed service transactions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StudentHttpIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired StudentRepository students;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void login() throws Exception {
        var user = users.saveAndFlush(new UserEntity("http-admin", "http-admin@example.org",
                passwords.encode("http-test-password"), Role.ADMIN, Instant.now()));
        userId = user.getId();
        var response = send("POST", "/api/auth/login", "{\"username\":\"http-admin\",\"password\":\"http-test-password\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        var tokens = json.readTree(response.body());
        accessToken = tokens.get("accessToken").asText();
        refreshToken = tokens.get("refreshToken").asText();
    }

    @Test
    void queryAndCrudWorkOverRealHttpAndChangesCommitToPostgres() throws Exception {
        String body = """
                {"isu":887766,"fio":"Иванов Иван Иванович","group":"M3301","notes":"HTTP-пример"}
                """;
        var created = send("POST", "/api/requests", body);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(students.findByIsu(887766)).isPresent();

        var query = send("QUERY", "/api/requests", "{\"search\":\"http-ПРИМЕР\",\"sort\":\"isu,desc\"}");
        assertThat(query.statusCode()).isEqualTo(200);
        assertThat(json.readTree(query.body()).at("/data/0/isu").asInt()).isEqualTo(887766);

        assertThat(send("PATCH", "/api/requests/887766", "{\"isForeigner\":true}").statusCode()).isEqualTo(200);
        assertThat(students.findByIsu(887766).orElseThrow().isForeigner()).isTrue();
        assertThat(send("PATCH", "/api/requests/887766", "{\"dorm\":1}").statusCode()).isEqualTo(422);
        assertThat(students.findByIsu(887766).orElseThrow().getDorm()).isNull();

        assertThat(send("PUT", "/api/requests/887766", body).statusCode()).isEqualTo(200);
        assertThat(students.findByIsu(887766).orElseThrow().isForeigner()).isFalse();
        assertThat(send("GET", "/api/requests/887766", null).statusCode()).isEqualTo(200);
        assertThat(send("DELETE", "/api/requests/887766", null).statusCode()).isEqualTo(204);
        assertThat(students.findByIsu(887766)).isEmpty();
        assertThat(send("GET", "/api/requests/887766", null).statusCode()).isEqualTo(404);
    }

    @AfterEach
    void cleanup() {
        students.findByIsu(887766).ifPresent(students::delete);
        if (userId != null) users.deleteById(userId);
    }

    @Test
    void concurrentRefreshAllowsExactlyOneRotation() throws Exception {
        var body = "{\"refreshToken\":\"" + refreshToken + "\"}";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/refresh"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        assertThat(java.util.List.of(first.get().statusCode(), second.get().statusCode()))
                .containsExactlyInAnyOrder(200, 401);
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (body != null) request.header("Content-Type", "application/json");
        if (accessToken != null && !path.startsWith("/api/auth/")) request.header("Authorization", "Bearer " + accessToken);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
