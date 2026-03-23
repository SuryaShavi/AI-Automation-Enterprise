-- ─────────────────────────────────────────────────────────────────────────────
-- V4  Workflow enhancements
--
-- Goals
--   1. Extend aieap.workflows with owner, trigger-type and config metadata.
--   2. Extend aieap.workflow_executions with completion tracking and error info.
--   3. Add aieap.workflow_triggers for schedule / event / webhook trigger rules.
--   4. Supporting indexes for all new columns.
--
-- Backward-compatible: every new NOT NULL column carries a DEFAULT value so
-- existing INSERT statements in WorkflowController continue to work unchanged.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── 1. Enhance aieap.workflows ────────────────────────────────────────────────
ALTER TABLE aieap.workflows
    ADD COLUMN IF NOT EXISTS description   TEXT,
    ADD COLUMN IF NOT EXISTS owner_user_id UUID        REFERENCES aieap.users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS trigger_type  VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS config_json   JSONB       NOT NULL DEFAULT '{}'::jsonb;

-- ── 2. Enhance aieap.workflow_executions ──────────────────────────────────────
ALTER TABLE aieap.workflow_executions
    ADD COLUMN IF NOT EXISTS completed_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS triggered_by  UUID        REFERENCES aieap.users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS error_message TEXT;

-- ── 3. New: aieap.workflow_triggers ──────────────────────────────────────────
-- Stores per-workflow repeating trigger rules (schedule, event, webhook).
CREATE TABLE IF NOT EXISTS aieap.workflow_triggers (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id   UUID         NOT NULL REFERENCES aieap.workflows(id) ON DELETE CASCADE,
    trigger_type  VARCHAR(50)  NOT NULL,         -- 'SCHEDULE' | 'EVENT' | 'WEBHOOK'
    schedule_cron VARCHAR(100),                   -- cron expression; set for SCHEDULE triggers
    event_type    VARCHAR(100),                   -- event name; set for EVENT triggers
    config_json   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_fired_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── 4. Indexes ────────────────────────────────────────────────────────────────

-- workflows
CREATE INDEX IF NOT EXISTS idx_workflows_owner
    ON aieap.workflows(owner_user_id);

CREATE INDEX IF NOT EXISTS idx_workflows_trigger_status
    ON aieap.workflows(trigger_type, status);

-- workflow_executions
CREATE INDEX IF NOT EXISTS idx_workflow_exec_status
    ON aieap.workflow_executions(status);

CREATE INDEX IF NOT EXISTS idx_workflow_exec_completed
    ON aieap.workflow_executions(completed_at DESC);

CREATE INDEX IF NOT EXISTS idx_workflow_exec_triggered_by
    ON aieap.workflow_executions(triggered_by);

-- workflow_triggers
CREATE INDEX IF NOT EXISTS idx_workflow_triggers_workflow
    ON aieap.workflow_triggers(workflow_id, enabled);

CREATE INDEX IF NOT EXISTS idx_workflow_triggers_type_enabled
    ON aieap.workflow_triggers(trigger_type, enabled);
