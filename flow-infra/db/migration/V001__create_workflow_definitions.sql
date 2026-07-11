-- V001 — Create workflow_definitions table
-- Tenant- and workflow-key-scoped versioned process templates (the BPMN-style step graph).
-- The ordered step list is stored as JSONB; one active row per (tenant_id, workflow_key).
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE workflow_definitions;

CREATE TABLE IF NOT EXISTS workflow_definitions (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    TEXT         NOT NULL,
    workflow_key TEXT         NOT NULL,
    name         TEXT         NOT NULL,
    version      INTEGER      NOT NULL DEFAULT 1
                              CHECK (version >= 1),
    steps        JSONB        NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- A (tenant, workflow_key) pair may hold at most one active definition.
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_definitions_active
    ON workflow_definitions (tenant_id, workflow_key)
    WHERE active;

-- Distinct versions of the same workflow are unique.
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_definitions_version
    ON workflow_definitions (tenant_id, workflow_key, version);

-- Tenant listing, most recently updated first.
CREATE INDEX IF NOT EXISTS idx_workflow_definitions_tenant
    ON workflow_definitions (tenant_id, updated_at DESC);
