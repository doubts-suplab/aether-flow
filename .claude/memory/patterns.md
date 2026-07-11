# Approved Patterns — Aether Flow

## Persistence
- **UPSERT:** `INSERT … ON CONFLICT (id) DO UPDATE SET …` for idempotent definition/instance/task saves. Explicit column lists always.
- **JSONB step graph:** `WorkflowDefinition.steps` serialised with Jackson and bound via `CAST(:steps AS jsonb)`; read back through `rs.getString` + `ObjectMapper` (records deserialise natively on Jackson 2.17).
- **Unique-active index:** a partial unique index on `(tenant_id, workflow_key) WHERE active` guarantees one active definition per workflow.
- **FK cascade:** `approval_tasks.instance_id … ON DELETE CASCADE` — deleting an instance removes its tasks.
- **Set-based escalation:** the sweep is a single `UPDATE` moving breached `PENDING` tasks to `ESCALATED`. No per-row round trips. Partial index on `(sla_due_at) WHERE outcome='PENDING'`.
- **Open-queue reads:** `WHERE outcome IN ('PENDING','ESCALATED') ORDER BY created_at ASC` — oldest waiting review first; partial index backs it.

## Domain
- **Immutable records** with compact-constructor validation; transitions return new instances (`moveTo`, `park`, `complete`, `reject`, `cancel`, `fail`, `approve`, `escalate`).
- **Factory methods:** `WorkflowDefinition.create(...)`, `WorkflowInstance.start(...)`, `ApprovalTask.raise(...)`, `WorkflowStep.{automated,humanApproval,end}(...)`, `FlowScope.of(...)`.
- **Graph validation in the record:** the definition rejects a graph without exactly one END, with duplicate keys, or with an unresolvable transition — the guarantee is inherited by every builder.
- **Terminal-state guard:** transition methods throw `IllegalStateException` from a terminal status, so a completed/rejected instance cannot be mutated.

## Orchestration
- **Drive loop:** advance the current step — human step → park + raise task + return; END → complete + return; else → move to next step + save + continue. Bounded by `MAX_TRANSITIONS` to guard malformed graphs.
- **Resume on decision:** approve records the decision, moves the parked instance to the gate's successor, and re-drives; reject stops the instance.
- **Version pinning:** an instance stores `definitionVersion`; the scaffold resolves the active definition to advance (single-version), leaving room for version-aware resume later.

## Spring wiring
- **Constructor injection only.** Beans declared in `FlowApiConfig` / `SlaEscalationConfig`; engine adapters live in `flow-engine` and are pure (no Spring annotations).
- **Config via `@Value` with env-backed defaults** in `application.yml` (deferral SLA minutes, escalation cron, enable flag).
- **Scoped scheduling:** `@EnableScheduling` sits on `SlaEscalationConfig` behind `@ConditionalOnProperty` so the sweep can be disabled entirely.

## API
- **Tenant scoping in the path:** `/api/v1/tenants/{tenantId}/…`; the Grid DEFER endpoint (`/api/v1/deferrals`) carries the tenant inside the decision projection, as Grid sends it.
- **Request DTO records:** `CreateWorkflowRequest`, `StepRequest`, `DecisionRequest`, `DeferralRequest` bound by Jackson; validation/domain errors → 400.
- **Status mapping:** unknown/absent definition → 404/409; already-decided task → 409; malformed graph → 400.
- **Null-safe views:** instance/task views use a `HashMap` because `completedAt`/`decidedBy`/`comment` may be null (`Map.of` rejects nulls).

## Testing
- **Unit tests** (`*Test`, surefire) for domain logic, the orchestration engine, and the gateway using in-memory fake stores (`InMemoryStores`) and `SimpleMeterRegistry`.
- **Integration tests** (`*IT`, Testcontainers `postgres:16`, Flyway-migrated) for JDBC adapters + the escalation sweep — run in CI where Docker is present.
