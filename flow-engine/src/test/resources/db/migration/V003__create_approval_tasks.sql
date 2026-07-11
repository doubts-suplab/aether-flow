-- V003 — Create approval_tasks table
-- Human review gates raised when an instance reaches a HUMAN_APPROVAL step. Carries an SLA
-- deadline; the escalation sweep flags breached PENDING tasks as ESCALATED (never auto-decides).
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE approval_tasks;

CREATE TABLE IF NOT EXISTS approval_tasks (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     TEXT         NOT NULL,
    instance_id   UUID         NOT NULL
                               REFERENCES workflow_instances (id) ON DELETE CASCADE,
    workflow_key  TEXT         NOT NULL,
    step_key      TEXT         NOT NULL,
    assigned_role TEXT         NOT NULL DEFAULT 'reviewer',
    outcome       TEXT         NOT NULL DEFAULT 'PENDING'
                               CHECK (outcome IN ('PENDING', 'APPROVED', 'REJECTED', 'ESCALATED')),
    sla_due_at    TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    decided_at    TIMESTAMPTZ,
    decided_by    TEXT,
    comment       TEXT
);

-- Review queue: open tasks for a role, oldest first.
CREATE INDEX IF NOT EXISTS idx_approval_tasks_queue
    ON approval_tasks (tenant_id, assigned_role, created_at)
    WHERE outcome IN ('PENDING', 'ESCALATED');

-- Escalation sweep scans breached PENDING tasks by deadline.
CREATE INDEX IF NOT EXISTS idx_approval_tasks_sla
    ON approval_tasks (sla_due_at)
    WHERE outcome = 'PENDING';

-- Resolve the open task for a parked instance.
CREATE INDEX IF NOT EXISTS idx_approval_tasks_instance
    ON approval_tasks (instance_id)
    WHERE outcome IN ('PENDING', 'ESCALATED');
