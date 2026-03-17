CREATE SCHEMA IF NOT EXISTS aieap;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS aieap.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    preferences_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aieap.roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aieap.user_roles (
    user_id UUID NOT NULL REFERENCES aieap.users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES aieap.roles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS aieap.integrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES aieap.users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DISCONNECTED',
    auth_type VARCHAR(30) NOT NULL DEFAULT 'OAUTH2',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    connected_at TIMESTAMPTZ,
    disconnected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider)
);

CREATE TABLE IF NOT EXISTS aieap.emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    integration_id UUID REFERENCES aieap.integrations(id),
    external_email_id VARCHAR(255) NOT NULL,
    sender_name VARCHAR(255),
    sender_email VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body_text TEXT,
    body_html TEXT,
    ai_summary TEXT,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    processing_status VARCHAR(30) NOT NULL DEFAULT 'INGESTED',
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (external_email_id)
);

CREATE TABLE IF NOT EXISTS aieap.tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_email_id UUID REFERENCES aieap.emails(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    assignee_user_id UUID REFERENCES aieap.users(id),
    created_by_user_id UUID REFERENCES aieap.users(id),
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    due_at TIMESTAMPTZ,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aieap.extracted_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id UUID NOT NULL REFERENCES aieap.emails(id) ON DELETE CASCADE,
    suggested_title VARCHAR(255) NOT NULL,
    suggested_description TEXT,
    confidence NUMERIC(5,4) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    accepted_task_id UUID REFERENCES aieap.tasks(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aieap.task_activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES aieap.tasks(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES aieap.users(id),
    activity_type VARCHAR(50) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aieap.documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID REFERENCES aieap.users(id),
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    processing_status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
    summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aieap.document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES aieap.documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    token_count INT NOT NULL,
    vector_id VARCHAR(255),
    citation_label VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, chunk_index)
);

CREATE TABLE IF NOT EXISTS aieap.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID REFERENCES aieap.users(id),
    report_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    request_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_payload JSONB,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    generated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS aieap.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES aieap.users(id) ON DELETE CASCADE,
    channel VARCHAR(30) NOT NULL DEFAULT 'IN_APP',
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    read_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aieap.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES aieap.users(id),
    service_name VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    trace_id VARCHAR(100) NOT NULL,
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aieap.idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    scope VARCHAR(100) NOT NULL,
    request_hash VARCHAR(255) NOT NULL,
    response_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS aieap.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_name VARCHAR(120) NOT NULL,
    payload_json JSONB NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON aieap.users(email);
CREATE INDEX IF NOT EXISTS idx_tasks_assignee_status ON aieap.tasks(assignee_user_id, status);
CREATE INDEX IF NOT EXISTS idx_tasks_due_at ON aieap.tasks(due_at);
CREATE INDEX IF NOT EXISTS idx_task_activity_task_id ON aieap.task_activity(task_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_emails_received_at ON aieap.emails(received_at DESC);
CREATE INDEX IF NOT EXISTS idx_extracted_tasks_email_id ON aieap.extracted_tasks(email_id);
CREATE INDEX IF NOT EXISTS idx_documents_owner_created ON aieap.documents(owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_document_chunks_document_id ON aieap.document_chunks(document_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_reports_owner_status ON aieap.reports(owner_user_id, status);
CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON aieap.notifications(user_id, read_at);
CREATE INDEX IF NOT EXISTS idx_integrations_user_provider ON aieap.integrations(user_id, provider);
CREATE INDEX IF NOT EXISTS idx_audit_logs_trace_id ON aieap.audit_logs(trace_id);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON aieap.outbox_events(status, created_at);

INSERT INTO aieap.roles (code, name)
VALUES ('ADMIN', 'Administrator'), ('EMPLOYEE', 'Employee')
ON CONFLICT (code) DO NOTHING;