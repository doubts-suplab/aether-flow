# Aether Flow — Progress Tracker

> **Scope:** This tracker covers **Aether Flow** (`suplab/aether-flow`) only.
> For ecosystem progress see [suplab/aether](https://github.com/suplab/aether).

---

**Active Phase:** Phase 1 — Orchestration Engine Hardening 🔄 (in progress)

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Orchestration Engine Hardening | 🔄 In progress | 2 |
| 2 | Human Approval & SLA Governance | ⏳ Planned | — |
| 3 | Grid Integration Deepening | ⏳ Planned | — |
| 4 | Kubernetes + Helm | ⏳ Planned | — |

---

## Phase 1 — Orchestration Engine Hardening 🔄

**Commit:** `feat(flow): add workflow instance cancellation with approval-task withdrawal`

### What was done (Session 2)
- **Instance cancellation** — an operator can cancel any non-terminal instance:
  - Domain: new terminal `ApprovalOutcome.WITHDRAWN` (closed without a human decision, not open, not decided) + `ApprovalTask.withdraw()`; `ApprovalOutcome.isResolved()`
  - Engine: `WorkflowEnginePort.cancel(tenant, instanceId, cancelledBy, reason)` — withdraws the instance's open approval task (if parked) then stops it in `CANCELLED`; terminal/unknown instances rejected
  - API: `POST …/workflows/{key}/instances/{id}/cancel` — 200 / 400 (no `cancelledBy`) / 404 (unknown) / 409 (terminal)
  - Migration `V004` — extends the `approval_tasks.outcome` CHECK to allow `WITHDRAWN`
- **Tests:** 62 unit tests green (5 new: withdraw transitions + outcome flags, cancel-parked/terminal/unknown); IT for `WITHDRAWN` upsert + queue exit
- **Docs synced:** README, architecture (V004 + cancellation flow 5.3), roadmap, glossary, patterns, session-log, index.html, CLAUDE.md

---

## Phase 0 — Scaffold ✅

**Commit:** `feat(flow): scaffold Aether Flow — workflow orchestration platform`

### What was done

**Maven project:**
- `pom.xml` — independent parent POM (`aether-flow-parent`), Spring Boot 3.3.5 BOM, Java 21, `--enable-preview`, `-parameters` flags, JaCoCo config (pluginManagement, mirroring the ecosystem)
- 4 modules: `flow-domain`, `flow-engine`, `flow-api`, `flow-infra`

**`flow-domain` — pure domain (no Spring):**
- `WorkflowDefinition` record: versioned step graph, validated on construction (exactly one END, unique keys, resolvable transitions); `startStep`, `stepByKey`, `nextStep`, `deactivate`
- `WorkflowStep` record + `StepType` enum (`AUTOMATED | AGENT | HUMAN_APPROVAL | END`), with SLA + assigned role on approval steps
- `WorkflowInstance` record + `WorkflowStatus` enum: immutable transitions (`moveTo/park/complete/reject/cancel/fail`), terminal-state guard
- `ApprovalTask` record + `ApprovalOutcome` enum: SLA deadline, `isBreached`, `approve/reject/escalate`
- `DeferredDecision` record: Grid's bounded DEFER projection (`CONFIDENCE_GATE = 0.8`, `isBelowGate`)
- `FlowScope` record: `(tenantId, workflowKey)` ownership + isolation key
- Ports: `WorkflowDefinitionStore`, `WorkflowInstanceStore`, `ApprovalTaskStore`, `WorkflowEnginePort`, `ApprovalGatewayPort`, `SlaEscalationPort`

**`flow-engine` — adapters + services:**
- `DefaultWorkflowOrchestrationService`: the state machine — start drives an instance to its first park/completion; approve advances past the gate; reject stops it; every transition persisted; loop guard
- `DefaultApprovalGateway`: Grid DEFER → canonical `grid-deferral` workflow, created on first use, parked with the decision's requested role
- `SlaEscalationService`: set-based `PENDING → ESCALATED` sweep for breached tasks
- `JdbcWorkflowDefinitionStore`: JSONB step graph (Jackson), unique-active per `(tenant, workflow_key)`, explicit column lists, `NamedParameterJdbcTemplate`, `ON CONFLICT` upsert
- `JdbcWorkflowInstanceStore`: durable state; scoped and tenant-only lookups
- `JdbcApprovalTaskStore`: open-queue reads (oldest first), per-instance open task

**`flow-api` — Spring Boot application:**
- `AetherFlowApplication`: port 8085, `scanBasePackages = "com.suplab.aether.flow"`
- `WorkflowDefinitionController`: POST create (graph validated → 400), GET list, GET active, DELETE — tenant scoped
- `WorkflowInstanceController`: POST start (→ first stable state), GET list by status, GET one
- `ApprovalTaskController`: GET role queue, POST approve / reject (resume via engine)
- `DeferredDecisionController`: `POST /api/v1/deferrals` Grid intake
- `SlaEscalationScheduler` + `SlaEscalationConfig`: `@Scheduled` sweep, Micrometer metrics, opt-out flag
- `FlowApiConfig`: wires all engine beans via constructor injection
- `application.yml`: port 8085, Flyway enabled, deferral SLA + escalation config; `Dockerfile` (multi-stage, non-root)

**`flow-infra` — infrastructure:**
- Flyway migrations V001–V003 (workflow_definitions, workflow_instances, approval_tasks)
- `docker/docker-compose.yml`: postgres-flow (port 5436) + aether-flow (port 8085)
- `k8s/`: namespace, deployment (probes, non-root, read-only fs), service + HPA + ConfigMap + Secret template

**Tests — 57 unit tests green:**
- `WorkflowDefinitionTest` (11), `WorkflowStepTest` (9), `WorkflowInstanceTest` (8), `ApprovalTaskTest` (10), `FlowScopeAndDeferralTest` (6)
- `DefaultWorkflowOrchestrationServiceTest` (8), `DefaultApprovalGatewayTest` (3) via in-memory fake stores
- `SlaEscalationSchedulerTest` (2): counter accumulation vs gauge-latest
- Testcontainers ITs (CI, `postgres:16`): `JdbcWorkflowDefinitionStoreIT` (4), `JdbcWorkflowInstanceStoreIT` (4), `JdbcApprovalTaskStoreIT` (4), `SlaEscalationServiceIT` (2)
- Full reactor `mvn verify` green; `flow-api` fat jar boots (Spring context wiring validated)

**`.claude/` setup:**
- Specialist agent definitions + memory files seeded with Flow context
- `CLAUDE.md` project brief, `aether.manifest.yaml`

**Docs:**
- `README.md`, `docs/index.html`, `docs/architecture.md`, `docs/roadmap.md`, `docs/progress.md`
- GitHub Actions: `ci.yml`, `quality-gate.yml`, `docker-build.yml`

---

## Phase 1 — Orchestration Engine Hardening 🔄 (2 of 4)

**Deliverable 1 — Instance cancellation + operator actions** (`feat(flow): add workflow instance
cancellation with approval-task withdrawal`): a non-terminal instance can be cancelled by an operator
(`POST …/instances/{id}/cancel`, `cancelledBy` required); if it is parked, its open approval task is
`withdraw()`-n (`WITHDRAWN` outcome — closed with no human decision, leaves the queue) and the instance
stops in `CANCELLED`. Migration **V004** widens the `approval_tasks.outcome` CHECK to allow `WITHDRAWN`.

**Deliverable 2 — Testcontainers coverage green in CI + operator stats**
(`feat(flow): operator stats endpoint + run Testcontainers ITs in CI`):
- `maven-failsafe-plugin` wired in the parent (pluginManagement) and activated in `flow-engine`, so the
  `*IT` Testcontainers tests (`JdbcWorkflowDefinitionStoreIT`, `JdbcWorkflowInstanceStoreIT`,
  `JdbcApprovalTaskStoreIT` incl. the cancellation-withdrawal case, `SlaEscalationServiceIT`) now run at
  `verify`. Previously no failsafe plugin existed, so surefire never ran `*IT` and the CI Postgres
  service was effectively unused.
- `GET …/instances/stats` — an operator status breakdown (per-status instance counts) that wires the
  previously-unused `WorkflowInstanceStore.countByStatus`.
- New `WorkflowInstanceControllerTest` (9, fake engine/store) — start / list / get / cancel / stats,
  incl. the required-`cancelledBy` 400 path and 404/409.
- `mvn -DskipITs verify` passes the JaCoCo 80% line gate; ITs run under failsafe in CI.

**Tests — 71 unit tests green (was 57).**

### Remaining Phase 1 (later)
- **Parallel / branching gateways** — the engine follows a linear `nextStepKey` (own increment).
- **Definition versioning + migration of in-flight instances** (own increment).

---

## Phase 1 — deliverable 3: definition versioning + version-pinned execution ✅

**Commit:** `feat(flow): definition versioning with version-pinned in-flight execution`

Registering a definition for an existing `workflowKey` now **publishes a new version** (`prior + 1`)
and retires the old one, and — crucially — a running instance is always executed against the exact
version it started under, closing a latent correctness gap.

- `WorkflowDefinition.supersede(name, steps)` — mints a new active definition at `version + 1` (new
  identity, same scope, graph validated on construction).
- `WorkflowDefinitionStore.findByVersion(scope, version)` (port + JDBC + in-memory fake) — resolve a
  specific version, active or not.
- `DefaultWorkflowOrchestrationService.approve` now resolves the definition by the **instance's**
  `definitionVersion` (`findByVersion`), not `findActive`. Previously a version published while an
  instance was parked would have driven that instance with the *new* graph — potentially a step that
  no longer exists. Now in-flight instances "pin and continue".
- `WorkflowDefinitionController` publishes a new version when one already exists: it builds and
  **validates** the new version *before* deactivating the old, so an invalid graph leaves the active
  version untouched; at most one version is active per scope.

**Tests — 79 unit tests green (was 71):**
- `WorkflowDefinitionVersioningTest` (3) — supersede bumps/validates; `WorkflowDefinitionControllerTest`
  (4) — v1 then v2-with-retire, invalid-new-graph-leaves-active-untouched
- `DefaultWorkflowOrchestrationServiceTest` — a decisive version-pinning test (publish a v2 without the
  parked instance's step; approve still resumes correctly on v1)
- `JdbcWorkflowDefinitionStoreIT` — `findByVersion` resolves each version independently of active
- `mvn -DskipITs verify` passes the JaCoCo 80% gate; ITs run under failsafe in CI.
