package ru.itmo.students;

import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.students.config.JwtProperties;
import ru.itmo.students.dto.*;
import ru.itmo.students.entity.*;
import ru.itmo.students.repository.*;
import ru.itmo.students.security.JwtTokens;
import ru.itmo.students.service.*;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Transactional
class AuthApiIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository tokens;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties properties;
    @Autowired AuthService auth;
    @Autowired StudentService students;
    @PersistenceContext EntityManager em;

    @Test
    void registrationAlwaysCreatesUserAndNeverReturnsPasswordOrHash() throws Exception {
        String body = "{\"username\":\"student\",\"email\":\"STUDENT@example.org\",\"password\":\"test-password\",\"role\":\"ADMIN\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        var stored = users.findByUsernameIgnoreCase("STUDENT").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("test-password");
        assertThat(passwords.matches("test-password", stored.getPasswordHash())).isTrue();
        assertThat(stored.getEmail()).isEqualTo("student@example.org");
    }

    @Test
    void registrationValidatesAndRejectsCaseInsensitiveDuplicates() throws Exception {
        register();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"STUDENT\",\"email\":\"other@example.org\",\"password\":\"test-password\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details.username").exists());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"another\",\"email\":\"STUDENT@example.org\",\"password\":\"test-password\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details.email").exists());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"email\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void loginReturnsJwtPairAndDatabaseStoresOnlyRefreshHash() throws Exception {
        register();
        var pair = login("STUDENT", "test-password");
        assertThat(pair.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(pair.get("expiresIn").asLong()).isEqualTo(properties.accessTtl().toSeconds());
        var refresh = pair.get("refreshToken").asText();
        assertThat(refresh.split("\\.")).hasSize(3);
        assertThat(tokens.findByTokenHash(JwtTokens.hash(refresh))).isPresent();
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(pair)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("student"))
                .andExpect(jsonPath("$.role").value("USER")).andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void wrongPasswordAndUnknownUserHaveTheSameError() throws Exception {
        register();
        var first = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"student\",\"password\":\"wrong-password\"}")).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        var second = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"unknown\",\"password\":\"wrong-password\"}")).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(first)).isEqualTo(json.readTree(second));
    }

    @Test
    void refreshRotatesAndLogoutRevokesTheReplacement() throws Exception {
        register();
        var original = login("student", "test-password");
        var replacement = refresh(original, 200);
        assertThat(replacement.get("refreshToken").asText()).isNotEqualTo(original.get("refreshToken").asText());
        assertThat(tokens.findByTokenHash(JwtTokens.hash(original.get("refreshToken").asText())).orElseThrow().getRevokedAt()).isNotNull();
        refresh(original, 401);
        mvc.perform(get("/api/requests").header("Authorization", bearer(replacement))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(replacement.get("refreshToken").asText())))
                .andExpect(status().isNoContent());
        refresh(replacement, 401);
    }

    @Test
    void accessAndRefreshTokensAreNotInterchangeableAndTamperingFails() throws Exception {
        register();
        var pair = login("student", "test-password");
        mvc.perform(get("/api/requests").header("Authorization", "Bearer " + pair.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(pair.get("accessToken").asText())))
                .andExpect(status().isUnauthorized());
        String[] parts = pair.get("accessToken").asText().split("\\.");
        var claims = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        claims.put("role", "ADMIN");
        parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(claims));
        mvc.perform(get("/api/requests").header("Authorization", "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "issuer", "audience", "purpose", "future"})
    void signedButInvalidAccessClaimsAreRejected(String kind) throws Exception {
        register();
        var user = users.findByUsernameIgnoreCase("student").orElseThrow();
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(kind.equals("issuer") ? "other" : properties.issuer())
                .audience(List.of(kind.equals("audience") ? "other" : properties.audience()))
                .subject(user.getId().toString()).id(UUID.randomUUID().toString()).issuedAt(now.minusSeconds(60))
                .notBefore(kind.equals("future") ? now.plusSeconds(60) : now.minusSeconds(60))
                .expiresAt(kind.equals("expired") ? now.minusSeconds(1) : now.plusSeconds(120))
                .claim("token_use", kind.equals("purpose") ? "refresh" : "access").build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").isString());
    }

    @Test
    void realJwtEnforcesRolesAndUsesCurrentDatabaseRole() throws Exception {
        register();
        var user = login("student", "test-password");
        String student = "{\"isu\":887766,\"fio\":\"Иванов Иван Иванович\",\"group\":\"M3301\"}";
        mvc.perform(get("/api/requests").header("Authorization", bearer(user))).andExpect(status().isOk());
        mvc.perform(post("/api/requests").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON).content(student))
                .andExpect(status().isForbidden());
        var admin = users.saveAndFlush(new UserEntity("admin-user", "admin@example.org", passwords.encode("admin-password"), Role.ADMIN, Instant.now()));
        var adminPair = login("admin-user", "admin-password");
        mvc.perform(post("/api/requests").header("Authorization", bearer(adminPair)).contentType(MediaType.APPLICATION_JSON).content(student))
                .andExpect(status().isCreated());
        em.createNativeQuery("UPDATE app_users SET role='USER' WHERE id=?").setParameter(1, admin.getId()).executeUpdate();
        em.clear();
        mvc.perform(delete("/api/requests/887766").header("Authorization", bearer(adminPair))).andExpect(status().isForbidden());
    }

    @Test
    void deletedUserCannotUsePreviouslyIssuedTokens() throws Exception {
        register();
        var pair = login("student", "test-password");
        users.delete(users.findByUsernameIgnoreCase("student").orElseThrow());
        users.flush();
        em.clear();
        mvc.perform(get("/api/requests").header("Authorization", bearer(pair))).andExpect(status().isUnauthorized());
        refresh(pair, 401);
    }

    @Test
    void bootstrapIsIdempotentAndCannotPromoteAnExistingUser() {
        var request = new RegistrationRequest("initial-admin", "admin@example.org", "admin-password");
        auth.provisionAdmin(request);
        auth.provisionAdmin(new RegistrationRequest("initial-admin", "admin@example.org", "different-password"));
        var admin = users.findByUsernameIgnoreCase("initial-admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwords.matches("admin-password", admin.getPasswordHash())).isTrue();
        auth.register(new RegistrationRequest("student", "student@example.org", "test-password"));
        assertThatThrownBy(() -> auth.provisionAdmin(new RegistrationRequest("student", "student@example.org", "admin-password")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.findByUsernameIgnoreCase("student").orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @WithMockUser(roles = "USER")
    void studentServiceAlsoChecksRolesWithoutAnHttpController() {
        assertThatThrownBy(() -> students.create(new StudentWriteRequest(123456, "Иванов Иван Иванович", "M3301", null, null, null, false, "")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void corsPermitsLiveServerAndQueryButRejectsUnknownOriginsWithJson() throws Exception {
        mvc.perform(options("/api/requests").header("Origin", "http://127.0.0.1:5500")
                        .header("Access-Control-Request-Method", "QUERY").header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5500"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("QUERY")));
        mvc.perform(get("/api/requests").header("Origin", "https://not-allowed.example"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").isString());
    }

    private void register() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"email\":\"student@example.org\",\"password\":\"test-password\"}"))
                .andExpect(status().isCreated());
    }
    private JsonNode login(String username, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new LoginRequest(username, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode refresh(JsonNode pair, int expected) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(pair.get("refreshToken").asText())))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    private String refreshBody(String token) throws Exception { return json.writeValueAsString(new RefreshRequest(token)); }
    private String bearer(JsonNode pair) { return "Bearer " + pair.get("accessToken").asText(); }
}
