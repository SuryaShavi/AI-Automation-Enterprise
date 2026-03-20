CREATE TABLE IF NOT EXISTS aieap.workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aieap.workflow_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES aieap.workflows(id) ON DELETE CASCADE,
    step_index INT NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workflow_id, step_index)
);

CREATE TABLE IF NOT EXISTS aieap.workflow_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES aieap.workflows(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    duration_ms INT NOT NULL DEFAULT 0,
    result_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_workflows_status ON aieap.workflows(status);
CREATE INDEX IF NOT EXISTS idx_workflow_steps_workflow ON aieap.workflow_steps(workflow_id, step_index);
CREATE INDEX IF NOT EXISTS idx_workflow_exec_workflow_started ON aieap.workflow_executions(workflow_id, started_at DESC);
