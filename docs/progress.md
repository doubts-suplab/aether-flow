# Aether Flow — Progress Tracker

> **Scope:** This tracker covers **Aether Flow** (`suplab/aether-flow`) only.
> For ecosystem progress see [suplab/aether](https://github.com/suplab/aether).

---

**Active Phase:** Phase 0 — Scaffold ✅ (complete)

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Orchestration Engine Hardening | ⏳ Planned | — |
| 2 | Human Approval & SLA Governance | ⏳ Planned | — |
| 3 | Grid Integration Deepening | ⏳ Planned | — |
| 4 | Kubernetes + Helm | ⏳ Planned | — |

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
