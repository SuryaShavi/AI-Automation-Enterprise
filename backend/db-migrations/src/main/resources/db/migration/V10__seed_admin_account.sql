-- Seed a default admin account for local development environments.
-- Renamed from V9 to V10 to fix version conflict.
-- This migration is idempotent and safe to re-run.

INSERT INTO aieap.users (email, user_code, first_name, last_name, password_hash, status, preferences_json, two_factor_enabled)
VALUES (
  'admin0@aieap.local',
  aieap.next_user_code(TRUE),
  'System',
  'Admin',
  crypt('Admin@123', gen_salt('bf')),
  'ACTIVE',
  '{}'::jsonb,
  FALSE
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO aieap.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM aieap.users u
JOIN aieap.roles r ON r.code = 'ADMIN'
WHERE u.email = 'admin0@aieap.local'
ON CONFLICT (user_id, role_id) DO NOTHING;
