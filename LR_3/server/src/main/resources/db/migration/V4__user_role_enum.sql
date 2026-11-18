CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');

-- Drop the varchar default before changing the column type; retain every row.
ALTER TABLE app_users
    DROP CONSTRAINT ck_users_role,
    ALTER COLUMN role DROP DEFAULT;

ALTER TABLE app_users
    ALTER COLUMN role TYPE user_role USING role::text::user_role,
    ALTER COLUMN role SET DEFAULT 'USER'::user_role;
