CREATE SEQUENCE IF NOT EXISTS aieap.admin_user_code_seq
    START WITH 2560000
    INCREMENT BY 1
    MINVALUE 2560000;

CREATE SEQUENCE IF NOT EXISTS aieap.employee_user_code_seq
    START WITH 2561000
    INCREMENT BY 1
    MINVALUE 2561000;

ALTER TABLE aieap.users
    ADD COLUMN IF NOT EXISTS user_code BIGINT;

WITH role_map AS (
    SELECT
        u.id,
        COALESCE(BOOL_OR(r.code = 'ADMIN'), FALSE) AS is_admin
    FROM aieap.users u
    LEFT JOIN aieap.user_roles ur ON ur.user_id = u.id
    LEFT JOIN aieap.roles r ON r.id = ur.role_id
    GROUP BY u.id
)
UPDATE aieap.users u
SET user_code = CASE
    WHEN role_map.is_admin
        THEN nextval('aieap.admin_user_code_seq')
    ELSE nextval('aieap.employee_user_code_seq')
END
FROM role_map
WHERE u.id = role_map.id
  AND u.user_code IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM aieap.users
        WHERE user_code IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot enforce NOT NULL because some user_code values are still NULL';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_users_user_code'
          AND connamespace = 'aieap'::regnamespace
    ) THEN
        ALTER TABLE aieap.users
            ADD CONSTRAINT uk_users_user_code UNIQUE (user_code);
    END IF;
END $$;

ALTER TABLE aieap.users
    ALTER COLUMN user_code SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_user_code
    ON aieap.users(user_code);

CREATE OR REPLACE FUNCTION aieap.next_user_code(is_admin BOOLEAN)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
BEGIN
    IF is_admin THEN
        RETURN nextval('aieap.admin_user_code_seq');
    END IF;

    RETURN nextval('aieap.employee_user_code_seq');
END;
$$;

CREATE OR REPLACE FUNCTION aieap.prevent_user_code_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.user_code IS DISTINCT FROM NEW.user_code THEN
        RAISE EXCEPTION 'user_code is immutable and cannot be changed';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_users_prevent_user_code_update
    ON aieap.users;

CREATE TRIGGER trg_users_prevent_user_code_update
    BEFORE UPDATE OF user_code
    ON aieap.users
    FOR EACH ROW
    EXECUTE FUNCTION aieap.prevent_user_code_update();

DO $$
DECLARE
    admin_max BIGINT;
    employee_max BIGINT;
BEGIN
    SELECT MAX(user_code)
      INTO admin_max
      FROM aieap.users
     WHERE user_code BETWEEN 2560000 AND 2560999;

    IF admin_max IS NOT NULL THEN
        PERFORM setval('aieap.admin_user_code_seq', admin_max, TRUE);
    END IF;

    SELECT MAX(user_code)
      INTO employee_max
      FROM aieap.users
     WHERE user_code >= 2561000;

    IF employee_max IS NOT NULL THEN
        PERFORM setval('aieap.employee_user_code_seq', employee_max, TRUE);
    END IF;
END $$;