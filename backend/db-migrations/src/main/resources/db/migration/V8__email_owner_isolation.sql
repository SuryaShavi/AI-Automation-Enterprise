ALTER TABLE aieap.emails
    ADD COLUMN IF NOT EXISTS owner_user_id UUID REFERENCES aieap.users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_emails_owner_received
    ON aieap.emails(owner_user_id, received_at DESC);

WITH first_active AS (
    SELECT id
    FROM aieap.users
    WHERE status = 'ACTIVE'
    ORDER BY created_at
    LIMIT 1
)
UPDATE aieap.emails e
SET owner_user_id = first_active.id
FROM first_active
WHERE e.owner_user_id IS NULL;
