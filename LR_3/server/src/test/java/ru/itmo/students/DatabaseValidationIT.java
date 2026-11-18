package ru.itmo.students;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.students.entity.*;
import ru.itmo.students.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/** SQL writes deliberately bypass all request DTOs and service validators. */
@SpringBootTest
@ActiveProfiles("integration-test")
class DatabaseValidationIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;

    static Stream<String> invalidWrites() {
        String student = "INSERT INTO students(isu,fio,student_group,dorm,room,settlement_date,notes) VALUES ";
        String user = "INSERT INTO app_users(username,email,password_hash,role) VALUES ";
        String hash = "'$2a$10$" + "a".repeat(53) + "'";
        return Stream.of(
            student + "(99999,'Иванов Иван Иванович','M3301',NULL,NULL,NULL,'')",
            student + "(1000000,'Иванов Иван Иванович','M3301',NULL,NULL,NULL,'')",
            student + "(123456,NULL,'M3301',NULL,NULL,NULL,'')",
            student + "(123456,'Ivanov Иван Иванович','M3301',NULL,NULL,NULL,'')",
            student + "(123456,'Иванов Иван','M3301',NULL,NULL,NULL,'')",
            student + "(123456,'Иванов Иван Иванович','bad',NULL,NULL,NULL,'')",
            student + "(123456,'Иванов Иван Иванович','M3301',0,1,'2026-01-01','')",
            student + "(123456,'Иванов Иван Иванович','M3301',1000,1,'2026-01-01','')",
            student + "(123456,'Иванов Иван Иванович','M3301',1,0,'2026-01-01','')",
            student + "(123456,'Иванов Иван Иванович','M3301',1,10000,'2026-01-01','')",
            student + "(123456,'Иванов Иван Иванович','M3301',1,1,'1999-12-31','')",
            student + "(123456,'Иванов Иван Иванович','M3301',1,1,'10000-01-01','')",
            student + "(123456,'Иванов Иван Иванович','M3301',1,1,'infinity','')",
            student + "(123456,'Иванов Иван Иванович','M3301',1,NULL,NULL,'')",
            student + "(123456,'Иванов Иван Иванович','M3301',NULL,NULL,NULL,repeat('я',501))",
            student + "(123456,'Иванов Иван Иванович','M3301',NULL,NULL,NULL,NULL)",
            user + "('x','test@example.org'," + hash + ",'USER')",
            user + "('bad name','test@example.org'," + hash + ",'USER')",
            user + "('valid','invalid-email'," + hash + ",'USER')",
            user + "('valid','test@@example.org'," + hash + ",'USER')",
            user + "('valid','test@example.org','plain-password','USER')",
            user + "('valid','test@example.org'," + hash + ",'SUPERADMIN')",
            user + "('valid','test@example.org'," + hash + ",NULL)"
        );
    }

    @ParameterizedTest
    @MethodSource("invalidWrites")
    @Transactional
    void databaseRejectsInvalidValuesWithoutJavaValidation(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).rootCause().isInstanceOf(SQLException.class)
                .satisfies(cause -> assertThat(((SQLException) cause).getSQLState()).matches("(22|23)[0-9A-Z]{3}"));
    }

    @Test
    @Transactional
    void boundaryValuesAndUnicodeNotesAreAccepted() {
        assertThat(jdbc.update("""
            INSERT INTO students(isu,fio,student_group,dorm,room,settlement_date,notes)
            VALUES (100000,'Ёлкин Пётр Александрович','M3301',1,1,'2000-01-01',repeat('😀',500)),
                   (999999,'Иванов Иван Иванович','Z9999',999,9999,'9999-12-31','')
            """)).isEqualTo(2);
    }

    @Test
    @Transactional
    void roleIsNativeEnumAndBothValuesRoundTripThroughEclipseLink() {
        assertThat(jdbc.queryForObject("""
            SELECT udt_name FROM information_schema.columns
            WHERE table_schema=current_schema() AND table_name='app_users' AND column_name='role'
            """, String.class)).isEqualTo("user_role");
        for (Role role : Role.values()) {
            String name = "enum-" + role.name().toLowerCase();
            users.saveAndFlush(new UserEntity(name, name + "@example.org", passwords.encode("test-password"), role, Instant.now()));
            assertThat(jdbc.queryForObject("SELECT role::text FROM app_users WHERE username=?", String.class, name))
                    .isEqualTo(role.name());
            assertThat(users.findByUsernameIgnoreCase(name).orElseThrow().getRole()).isEqualTo(role);
        }
    }

    @Test
    void migrationFromV3KeepsExistingUsersStudentsAndRefreshSessions() {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway old = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("3").load();
        try {
            old.migrate();
            String hash = passwords.encode("test-password");
            jdbc.update("INSERT INTO " + schema + ".app_users(username,email,password_hash,role) VALUES ('existing','existing@example.org',?,'ADMIN')", hash);
            jdbc.update("INSERT INTO " + schema + ".students(isu,fio,student_group) VALUES (123456,'Иванов Иван Иванович','M3301')");
            UUID token = UUID.randomUUID();
            jdbc.update("INSERT INTO " + schema + ".refresh_tokens(id,user_id,token_hash,created_at,expires_at) VALUES (?,1,?,now(),now()+interval '1 day')", token, "a".repeat(64));
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForObject("SELECT role::text FROM " + schema + ".app_users", String.class)).isEqualTo("ADMIN");
            assertThat(jdbc.queryForObject("SELECT password_hash FROM " + schema + ".app_users", String.class)).isEqualTo(hash);
            assertThat(jdbc.queryForObject("SELECT isu FROM " + schema + ".students", Integer.class)).isEqualTo(123456);
            assertThat(jdbc.queryForObject("SELECT id FROM " + schema + ".refresh_tokens", UUID.class)).isEqualTo(token);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
