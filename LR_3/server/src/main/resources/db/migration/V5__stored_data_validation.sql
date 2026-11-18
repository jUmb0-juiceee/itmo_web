-- Validate stored values even when writes bypass the Java services.
-- Existing length, nullability, unique and relationship constraints remain in V1.
ALTER TABLE students
    DROP CONSTRAINT ck_students_fio,
    ADD CONSTRAINT ck_students_fio CHECK (
        fio ~ '^[А-ЯЁ][а-яё-]+ [А-ЯЁ][а-яё-]+ [А-ЯЁ][а-яё-]+$'),
    DROP CONSTRAINT ck_students_date,
    ADD CONSTRAINT ck_students_date CHECK (
        settlement_date BETWEEN DATE '2000-01-01' AND DATE '9999-12-31');

ALTER TABLE app_users
    ADD CONSTRAINT ck_users_username_format CHECK (
        char_length(username) BETWEEN 3 AND 64
        AND username ~ '^[A-Za-z0-9_][A-Za-z0-9_.-]*$'),
    ADD CONSTRAINT ck_users_email_format CHECK (
        email ~ '^[^[:space:]@]+@[^[:space:]@]+$'),
    ADD CONSTRAINT ck_users_password_bcrypt CHECK (
        password_hash ~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$');

-- Password length is checked before hashing in Java, not against the BCrypt text.
-- PostgreSQL char_length counts Unicode code points, as the notes validator does.
