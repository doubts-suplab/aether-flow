# Aether Flow — Architecture

> **Scope:** This document covers **Aether Flow** (`suplab/aether-flow`) only.
> For the ecosystem-wide view see [suplab/aether](https://github.com/suplab/aether).

---

## 1. Purpose & Position

Aether Flow owns the **Workflows** capability of the Aether ecosystem: long-running, human-in-the-loop processes. It is a **platform layer** — above the runtime (Grid) and cognitive (Core) layers, below domain products.

```
Domain Products  (aether-enterprise, domain apps)
        ↓
Platform Layer   →  aether-flow  ← this repo   (alongside aether-memory, aether-vault)
        ↓
Runtime Layer    (aether-grid)
        ↓
Cognitive Layer  (aether-core)
```

Flow is distinct from the memory and knowledge layers: Core owns *personal* memory, Memory owns *shared team* memory, Vault owns *documents/knowledge*, and Flow owns **processes and the human decisions that drive them**. Flow consumes Grid at the DEFER seam (Grid → Flow, never the reverse) and never stores memory or knowledge of its own.

---

## 2. Module Boundaries

| Module | Package root | Responsibility |
|---|---|---|
| `flow-domain` | `com.suplab.aether.flow.domain` / `.ports` | Pure records + port interfaces. No framework. |
| `flow-engine` | `com.suplab.aether.flow.engine.*` | JDBC stores, orchestration state machine, Grid DEFER gateway, SLA escalation. |
| `flow-api` | `com.suplab.aether.flow.api.*` | Spring Boot app, REST controllers, Flyway, scheduling, config. |
| `flow-infra` | — | Docker Compose, Kubernetes manifests, migration reference copies. |

Dependency direction is strictly inward: `flow-api → flow-engine → flow-domain`. The domain never depends on Spring.

---

## 3. Domain Model

```
WorkflowDefinition
  id, tenantId, workflowKey, name, version, steps[], active, createdAt, updatedAt
  ├── validated on construction: exactly one END, unique keys, resolvable transitions
  ├── startStep()          → first declared step
  ├── nextStep(step)       → graph successor (empty for END)
  └── deactivate()         → active=false (running instances unaffected)

WorkflowStep     = (key, name, type, slaMinutes, assignedRole, nextStepKey, reworkStepKey)
                   reworkStepKey: HUMAN_APPROVAL reject branch (rework loop); null ⇒ reject terminates
StepType         = AUTOMATED | AGENT | HUMAN_APPROVAL | END
FlowScope        = (tenantId, workflowKey)   — the ownership + isolation key

WorkflowInstance
  id, tenantId, workflowKey, definitionVersion, businessKey, currentStepKey,
  status (RUNNING|WAITING_APPROVAL|COMPLETED|REJECTED|CANCELLED|FAILED), startedAt, updatedAt, completedAt
  ├── moveTo(step) / park(step)      → advance or park (WAITING_APPROVAL)
  └── complete() / reject() / cancel() / fail()   → terminal transitions (immutable)

ApprovalTask
  id, tenantId, instanceId, workflowKey, stepKey, assignedRole,
  outcome (PENDING|APPROVED|REJECTED|ESCALATED|WITHDRAWN), slaDueAt, createdAt, decidedAt, decidedBy, comment
  ├── isBreached(asOf)               → open and past SLA deadline
  ├── approve/reject/escalate        → decision or visibility transitions
  └── withdraw()                     → closed (no human decision) when its instance is cancelled

DeferredDecision = (correlationId, tenantId, agentId, summary, confidence, requestedRole, receivedAt)
                   — Grid's bounded DEFER projection; CONFIDENCE_GATE = 0.8
```

### Ports

| Port | Implementation | Purpose |
|---|---|---|
| `WorkflowDefinitionStore` | `JdbcWorkflowDefinitionStore` | Persist/retrieve definitions (JSONB step graph); scoped |
| `WorkflowInstanceStore` | `JdbcWorkflowInstanceStore` | Persist every instance transition; the durable state |
| `ApprovalTaskStore` | `JdbcApprovalTaskStore` | Persist the human review queue; open-task lookups |
| `WorkflowEnginePort` | `DefaultWorkflowOrchestrationService` | Start / advance instances; resume on approve/reject; **cancel** (stops the instance, withdraws its open task) |
| `ApprovalGatewayPort` | `DefaultApprovalGateway` | Grid DEFER → parked human-approval workflow |
| `SlaEscalationPort` | `SlaEscalationService` | Set-based sweep marking breached PENDING tasks ESCALATED |

---

## 4. Data Model (PostgreSQL 16)

| Migration | Object | Notes |
|---|---|---|
| `V001` | `workflow_definitions` | Versioned templates; JSONB `steps`; unique-active partial index on `(tenant_id, workflow_key)`; unique `(tenant_id, workflow_key, version)` |
| `V002` | `workflow_instances` | Durable execution state; index on `(tenant_id, workflow_key, status, updated_at)` and on `business_key` |
| `V003` | `approval_tasks` | Human review queue; `instance_id` FK `ON DELETE CASCADE`; partial index for the open queue and for the SLA sweep (`outcome='PENDING'` by `sla_due_at`) |
| `V004` | `approval_tasks.outcome` CHECK | Extends the outcome constraint to allow `WITHDRAWN` (a task closed because its instance was cancelled) |

Flow owns **no** vector store or embedding — the step graph is plain JSONB, everything else is relational. This keeps Flow single-store on PostgreSQL like the rest of the ecosystem, with no LLM runtime dependency.

---

## 5. Key Flows

### 5.1 Define & start a workflow
1. `POST …/workflows` → build `WorkflowStep`s from the request; the graph is validated on construction. The first registration for a `workflowKey` is **version 1**; each later registration **publishes a new version** (`prior + 1`, `supersede`) and retires the previously active one. The new version is validated *before* the old is deactivated, so an invalid graph leaves the active version untouched. At most one version is active per scope.
2. `POST …/workflows/{key}/instances` → `WorkflowEnginePort.start` reads the **active** version, creates an instance pinned to that `definitionVersion`, and **drives** it: automated/agent steps advance immediately; a `HUMAN_APPROVAL` step parks the instance (`WAITING_APPROVAL`) and raises an `ApprovalTask`; `END` completes it.

> **Version-pinned execution.** A running instance stores the `definitionVersion` it started under, and the engine resolves each instance against **that** version (`findByVersion`), never the currently-active one. Publishing a new version while an instance is parked therefore never changes how the in-flight instance resumes — the *migration of in-flight instances* is "pin and continue".

### 5.2 Human approval (resume)
1. `GET …/approvals?role=` → open tasks for a role, oldest first.
2. `POST …/approvals/{taskId}/approve` → `engine.approve` records the decision, moves the parked instance to the gate's successor, and drives onward to the next park or completion.
3. `POST …/approvals/{taskId}/reject` → `engine.reject`: if the approval step declares a `reworkStepKey`, the instance **branches** to that rework step and drives on (a rework loop — e.g. `review → fix → review`), making the graph non-linear; otherwise the instance stops in `REJECTED`. The `MAX_TRANSITIONS` guard bounds any loop.

### 5.3 Cancellation (operator withdrawal)
1. `POST …/instances/{id}/cancel` → `WorkflowEnginePort.cancel` loads the instance; a terminal instance is rejected (409).
2. If the instance is parked at an approval gate, its open task is `withdraw()`-n (`WITHDRAWN` — closed without a human decision, leaves the queue).
3. The instance transitions to `CANCELLED` and is persisted. Escalation and the review queue never surface a withdrawn task again.
4. `GET …/instances/stats` returns per-status instance counts for the scope — an operator view over the whole workflow (RUNNING, WAITING_APPROVAL, COMPLETED, CANCELLED, …).

### 5.4 SLA escalation (set-based)
1. Scheduler (`@Scheduled`, default every 5 min) → `SlaEscalationPort.sweep`.
2. Single `UPDATE`: `PENDING` tasks with `sla_due_at < NOW()` become `ESCALATED` (still open — a human must decide).
3. Micrometer: `aether.flow.escalation.escalated` counter, `aether.flow.approvals.open` gauge.

### 5.5 Grid DEFER intake
1. Grid's confidence gate (`confidence < 0.8`) defers a decision and POSTs a bounded `DeferredDecision` to `/api/v1/deferrals`.
2. `ApprovalGatewayPort.accept` ensures a canonical `grid-deferral` definition exists for the tenant, starts an instance, parks it at the review gate, and raises an `ApprovalTask` routed to `requestedRole`.
3. The reviewer's approve/reject on that task is the answer Grid awaits, correlated by `correlationId`.

---

## 6. Multi-Tenancy & Isolation

- Every definition, instance, and approval query is scoped by `tenant_id` (and `workflow_key` where the scope demands it). There is no cross-tenant read path.
- A workflow definition is validated on construction — a malformed step graph never persists and never runs.
- The Grid DEFER projection carries no Grid internals, no PII, and no raw request payloads — only a bounded summary and coarse provenance.

---

## 7. Configuration Surface

Reads from environment variables (never hardcoded). Defaults target local Docker Compose. See `README.md` for the full table. The deferral SLA budget and the escalation schedule are configurable; the escalation sweep can be disabled entirely.

---

## 8. Standalone Guarantee

Aether Flow has no compile-time or runtime dependency on Core, Grid, Memory, or Vault. It boots, migrates, serves, and runs its escalation sweep entirely on its own PostgreSQL schema (`aether_flow`). The Grid integration is an inbound REST seam — Flow accepts deferrals when Grid sends them, but requires nothing from Grid to run.
