-- V002 — Create workflow_instances table
-- Running (and finished) executions of a workflow definition. Persisting every state transition
-- here is what makes workflow state durable across service restarts.
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE workflow_instances;

CREATE TABLE IF NOT EXISTS workflow_instances (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          TEXT         NOT NULL,
    workflow_key       TEXT         NOT NULL,
    definition_version INTEGER      NOT NULL,
    business_key       TEXT,
    current_step_key   TEXT         NOT NULL,
    status             TEXT         NOT NULL DEFAULT 'RUNNING'
                                    CHECK (status IN ('RUNNING', 'WAITING_APPROVAL', 'COMPLETED',
                                                      'REJECTED', 'CANCELLED', 'FAILED')),
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at       TIMESTAMPTZ
);

-- Primary scoped access path: instances of a workflow in a given status, newest first.
CREATE INDEX IF NOT EXISTS idx_workflow_instances_scope_status
    ON workflow_instances (tenant_id, workflow_key, status, updated_at DESC);

-- Correlate an instance to a caller's domain object.
CREATE INDEX IF NOT EXISTS idx_workflow_instances_business_key
    ON workflow_instances (tenant_id, business_key);
