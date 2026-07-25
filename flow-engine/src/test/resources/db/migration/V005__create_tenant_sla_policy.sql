-- V005 — Per-tenant SLA policy + escalation-level tracking on approval tasks
-- Phase 2 (Human Approval & SLA Governance): a tenant configures an SLA budget and an ordered
-- escalation chain of roles; a breached open task is routed up that chain by the escalation sweep,
-- and its position in the chain is tracked by approval_tasks.escalation_level.
-- Lock risk: LOW (new table + additive column with a default)
-- Rollback: DROP TABLE tenant_sla_policy; ALTER TABLE approval_tasks DROP COLUMN escalation_level;

CREATE TABLE IF NOT EXISTS tenant_sla_policy (
    tenant_id           TEXT         PRIMARY KEY,
    default_sla_minutes INTEGER      NOT NULL DEFAULT 60 CHECK (default_sla_minutes >= 0),
    -- Ordered, comma-separated list of roles to escalate through ('' = no chain, flag-only).
    escalation_chain    TEXT         NOT NULL DEFAULT '',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- How many times a task has been escalated up the chain (0 = never). Drives the next hop.
ALTER TABLE approval_tasks
    ADD COLUMN IF NOT EXISTS escalation_level INTEGER NOT NULL DEFAULT 0
        CHECK (escalation_level >= 0);
