INSERT INTO aieap.users (email, first_name, last_name, password_hash, status, preferences_json, two_factor_enabled)
VALUES
    ('admin@aieap.local', 'Ava', 'Admin', '$2a$10$mz0oxgXjHPGm2slDfMQQG.IoOkI7gqzJ5x0wX2bypgLxAy/2wJLdi', 'ACTIVE', '{"theme":"light","dashboardLayout":"executive"}', TRUE),
    ('employee@aieap.local', 'Evan', 'Employee', '$2a$10$mz0oxgXjHPGm2slDfMQQG.IoOkI7gqzJ5x0wX2bypgLxAy/2wJLdi', 'ACTIVE', '{"theme":"light","dashboardLayout":"personal"}', FALSE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO aieap.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM aieap.users u
JOIN aieap.roles r ON (u.email = 'admin@aieap.local' AND r.code = 'ADMIN') OR (u.email = 'employee@aieap.local' AND r.code = 'EMPLOYEE')
ON CONFLICT (user_id, role_id) DO NOTHING;