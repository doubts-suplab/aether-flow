-- V004 — Allow the WITHDRAWN approval-task outcome
-- Cancelling a workflow instance withdraws its open approval task: the review is moot, so the task
-- closes without a human decision and leaves the queue. This extends the outcome CHECK constraint.
-- Lock risk: LOW (constraint swap on a small table; no data rewrite)
-- Rollback: re-add the constraint without 'WITHDRAWN' (requires no WITHDRAWN rows to exist).

ALTER TABLE approval_tasks DROP CONSTRAINT IF EXISTS approval_tasks_outcome_check;

ALTER TABLE approval_tasks ADD CONSTRAINT approval_tasks_outcome_check
    CHECK (outcome IN ('PENDING', 'APPROVED', 'REJECTED', 'ESCALATED', 'WITHDRAWN'));
