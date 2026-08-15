-- V006 — Business-hours calendar on the per-tenant SLA policy
-- Phase 2 (Human Approval & SLA Governance) follow-up: a tenant may configure a working-hours
-- calendar so SLA budgets are measured in working time — overnight and weekend hours do not count
-- against a reviewer's deadline. All columns are nullable; NULL across the set = 24/7 (the prior
-- behaviour), so existing rows are unaffected.
-- Lock risk: LOW (additive nullable columns on a small config table)
-- Rollback: ALTER TABLE tenant_sla_policy
--             DROP COLUMN business_zone, DROP COLUMN business_start,
--             DROP COLUMN business_end,  DROP COLUMN business_days;

ALTER TABLE tenant_sla_policy
    ADD COLUMN IF NOT EXISTS business_zone  TEXT,   -- IANA zone id (e.g. 'Europe/London'); NULL = 24/7
    ADD COLUMN IF NOT EXISTS business_start TIME,   -- daily opening time (inclusive)
    ADD COLUMN IF NOT EXISTS business_end   TIME,   -- daily closing time (exclusive)
    ADD COLUMN IF NOT EXISTS business_days  TEXT;   -- comma-separated DayOfWeek names (e.g. 'MONDAY,...')
