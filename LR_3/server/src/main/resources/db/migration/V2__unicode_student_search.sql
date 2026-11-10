-- PostgreSQL 17's built-in Unicode collation keeps Cyrillic lower() independent
-- of the operating system / initdb locale (including clusters created with C).
ALTER TABLE students
    ALTER COLUMN fio TYPE VARCHAR(150) COLLATE "pg_c_utf8",
    ALTER COLUMN notes TYPE VARCHAR(500) COLLATE "pg_c_utf8";
