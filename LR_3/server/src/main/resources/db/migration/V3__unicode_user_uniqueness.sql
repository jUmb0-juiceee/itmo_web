-- The lower(...) unique indexes must also behave consistently for Unicode email.
ALTER TABLE app_users
    ALTER COLUMN username TYPE VARCHAR(64) COLLATE "pg_c_utf8",
    ALTER COLUMN email TYPE VARCHAR(254) COLLATE "pg_c_utf8";
