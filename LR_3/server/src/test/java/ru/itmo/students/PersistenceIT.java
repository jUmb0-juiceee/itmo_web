package ru.itmo.students;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.eclipse.persistence.jpa.JpaEntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.students.entity.*;
import ru.itmo.students.repository.*;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requires a dedicated PostgreSQL test database. Every test's data changes are rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Transactional
class PersistenceIT {
    @Autowired StudentRepository students;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository tokens;
    @Autowired EntityManagerFactory factory;
    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;
    @PersistenceContext EntityManager em;

    @Test
    void usesEclipseLinkAndAppliesTheInitialMigration() {
        assertThat(factory.unwrap(JpaEntityManagerFactory.class)).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void studentKeepsInternalIdentityWhenIsuChanges() {
        var student = students.saveAndFlush(student(123456));
        var id = student.getId();
        student.replace(654321, student.getFio(), student.getGroup(), null, null, null, false, "Изменено");
        students.flush();
        em.clear();

        assertThat(students.findByIsu(123456)).isEmpty();
        var loaded = students.findByIsu(654321).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getNotes()).isEqualTo("Изменено");
    }

    @Test
    void databaseRejectsDuplicateIsu() {
        students.saveAndFlush(student(123456));
        assertThatThrownBy(() -> students.saveAndFlush(student(123456)))
                .rootCause().isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23505");
    }

    @Test
    void databaseRejectsPartiallyFilledSettlementFields() {
        var student = new StudentEntity(123456, "Иванов Иван Иванович", "M3301", 1, null, null, false, "");
        assertThatThrownBy(() -> students.saveAndFlush(student))
                .rootCause().isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23514");
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        users.saveAndFlush(user("student", "one@example.org"));
        assertThatThrownBy(() -> users.saveAndFlush(user("STUDENT", "two@example.org")))
                .rootCause().isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23505");
    }

    @Test
    void emailsAreUniqueIgnoringCase() {
        users.saveAndFlush(user("one", "student@example.org"));
        assertThatThrownBy(() -> users.saveAndFlush(user("two", "STUDENT@example.org")))
                .rootCause().isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23505");
    }

    @Test
    void unicodeEmailUniquenessDoesNotDependOnTheDatabaseLocale() {
        users.saveAndFlush(user("one", "Почта@example.org"));
        assertThatThrownBy(() -> users.saveAndFlush(user("two", "почта@example.org")))
                .rootCause().isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23505");
    }

    @Test
    void refreshTokenPersistsUserUuidAndExpiryAndIsDeletedWithUser() {
        var user = users.saveAndFlush(user("student", "student@example.org"));
        var created = Instant.parse("2026-11-08T12:00:00Z");
        var expires = created.plusSeconds(3600);
        var hash = "a".repeat(64);
        var token = tokens.saveAndFlush(new RefreshTokenEntity(UUID.randomUUID(), user, hash, created, expires));
        var id = token.getId();
        em.clear();

        var loaded = tokens.findByTokenHash(hash).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getUser().getUsername()).isEqualTo("student");
        assertThat(loaded.getUser().getRole()).isEqualTo(Role.USER);
        assertThat(loaded.getCreatedAt()).isEqualTo(created);
        assertThat(loaded.getExpiresAt()).isEqualTo(expires);
        loaded.revoke(created.plusSeconds(60));
        tokens.flush();
        em.clear();
        assertThat(tokens.findById(id).orElseThrow().getRevokedAt()).isEqualTo(created.plusSeconds(60));

        users.deleteById(user.getId());
        users.flush();
        em.clear();
        assertThat(tokens.findById(id)).isEmpty();
    }

    @Test
    void healthEndpointChecksDatabaseWithoutExposingConnectionDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void studentRoutesAreClosedUntilAuthenticationIsImplemented() throws Exception {
        mvc.perform(get("/api/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Требуется авторизация"));
    }

    private StudentEntity student(int isu) {
        return new StudentEntity(isu, "Иванов Иван Иванович", "M3301", null, null, null, false, "");
    }

    private UserEntity user(String username, String email) {
        var hash = new BCryptPasswordEncoder().encode("test-only-password");
        return new UserEntity(username, email, hash, Role.USER, Instant.parse("2026-11-08T12:00:00Z"));
    }
}
