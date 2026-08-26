# Aether Flow — Progress Tracker

> **Scope:** This tracker covers **Aether Flow** (`suplab/aether-flow`) only.
> For ecosystem progress see [suplab/aether](https://github.com/suplab/aether).

---

**Active Phase:** Phase 1 revisited — parallel AND fork/join gateways delivered (the last deferred Phase 1 item); Phases 2–4 core complete

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Orchestration Engine Hardening | ✅ Complete (incl. parallel AND fork/join) | 2, 8 |
| 2 | Human Approval & SLA Governance | ✅ Core complete (policy + chains + reassign + notify + metrics + business hours) | 5 |
| 3 | Grid Integration Deepening | ✅ Core complete (idempotent intake + outcome callback + agent-step execution + GDPR deferral erasure) | 6 |
| 4 | Kubernetes + Helm | ✅ Core complete (Helm chart + HPA + AWS/OpenShift values + release workflow) | 7 |

---

## Phase 1 — Parallel AND fork/join gateways ✅ (session 8)

**Commit:** `feat(flow): parallel AND fork/join gateways`

Closes the last deferred Phase 1 item — the engine graduates from single-path (linear + exclusive
rework branches) to a genuine **parallel AND-split/join**.

### What was done
- **Domain:** two new `StepType`s — `FORK` (an AND-split) and `JOIN` (an AND-join) — and a new
  `WorkflowStep.branchKeys` component naming a fork's parallel branch heads (empty for every other
  type). Factories `WorkflowStep.fork(key, name, branchKeys, joinKey)` and
  `WorkflowStep.join(key, name, nextStepKey)`; `StepType.isFork()`/`isJoin()` predicates.
- **Validation:** `WorkflowDefinition` now validates each fork/join pair — a FORK (≥2 branches) must
  converge on a JOIN, and every branch, followed transitively, must reach exactly that join without
  looping. Branches carry AUTOMATED/AGENT steps only — a human-approval gate, a nested FORK, or the
  END step inside a branch is rejected, keeping the single-token park model intact.
- **Engine:** `DefaultWorkflowOrchestrationService.drive` runs a FORK by executing every branch
  synchronously to the join (invoking the agent on AGENT steps, best-effort), then advancing past the
  JOIN to the single continuation. The instance stays on the fork until the whole block resolves — no
  concurrent parking.
- **API:** `StepRequest`/step view gain `branchKeys` so a fork/join graph can be created and read over
  REST. Steps still persist as JSONB (Jackson) — no migration, backward compatible (absent
  `branchKeys` deserialises to empty).

### Follow-up (documented, not regressed)
- A human-approval gate *inside* a parallel branch needs a multi-token instance model (concurrent
  WAITING_APPROVAL parks) and remains a Phase 1 follow-up; the synchronous AND fork/join above is the
  delivered increment.

### Constraints upheld
- Definitions still validate on construction — a malformed fork/join never persists.
- No new cross-tenant path; the engine remains framework-free (ports only); AGENT invocation in a
  branch is best-effort and never blocks progression.

---

## Phase 4 — Kubernetes + Helm ✅ (session 7 — Helm chart, HPA, multi-target values, release workflow)

**Commit:** `feat(flow): Kubernetes Helm chart — HPA, AWS/OpenShift value sets, release workflow`

The Dockerfile, `docker-build.yml`, and raw k8s manifests already existed; Phase 4 adds the production
Helm chart so Flow deploys the same way as the rest of the ecosystem (vanilla K8s, AWS EKS, OpenShift).

- Helm chart `flow-infra/helm/aether-flow/` (single-app, mirroring Core/Grid's hardened charts):
  `Chart.yaml` + `values.yaml` + `values-aws.yaml` (ALB + IRSA) + `values-openshift.yaml` (Route + SCC);
  templates for namespace, serviceaccount (token off), configmap (escalation + Grid-seam + notification
  URLs), service (ClusterIP 8085), deployment (rolling update, topology spread, non-root uid 1000,
  read-only rootfs, dropped caps, probes, config-checksum rollout), HPA (min 2 / max 8 / CPU 70%),
  ingress, route, servicemonitor, NOTES. Secrets never in-chart (pre-existing `existingSecret`).
- CI: `.github/workflows/helm-release.yml` — lint all value sets + `helm template` dry-run on changes
  under `flow-infra/helm/**`, then package + push OCI chart to GHCR on `main`.
- Verified: pure-YAML validated, all template includes defined; `helm lint`/`template` run in CI.

---

## Phase 3 — Grid Integration Deepening ✅ (session 6 — agent-step execution + GDPR deferral erasure)

**Commit:** `feat(flow): agent-step execution + right-to-erasure for Grid deferrals`

Completing Phase 3 alongside the closed loop below: `AGENT` steps now invoke a (config-gated) Grid
agent, and a deferral and its approval history can be erased on a right-to-erasure request.

### What was done

**Agent-step execution:**
- `WorkflowStep.agent(key, name, nextStepKey)` factory; `AgentStepInvoker` port (domain) with a
  `NO_OP` default. The orchestration engine invokes it as an instance passes an `AGENT` step, then
  advances regardless — best-effort augmentation, never a park or a gate. `HttpGridAgentInvoker`
  (engine) POSTs a bounded routing envelope (tenant, workflow/business/step keys — no PII) when
  `aether.flow.grid.agent-url` is set; a failing call is logged and swallowed.

**GDPR erasure (right to erasure, Art. 17):**
- `DeferralErasureResult` domain record + `ApprovalErasurePort`; `DefaultApprovalErasureService`
  (engine) erases a deferral by `correlationId` within a tenant — deletes its approval tasks first
  (child rows), then the `grid-deferral` instance, and reports the counts. Precise (per correlation,
  not tenant-wide) and idempotent. New store deletes: `ApprovalTaskStore.deleteByInstance` +
  `WorkflowInstanceStore.deleteByBusinessKey` (JDBC + in-memory).
- `DELETE /api/v1/tenants/{tenantId}/deferrals/{correlationId}` — 200 with counts, 404 when nothing
  matched.

### Constraints upheld
- Agent invocation is best-effort — a failing agent never blocks workflow progression.
- Erasure is tenant-scoped (no cross-tenant deletion path) and correlation-precise.
- Flow still runs standalone: `NO_OP` agent invoker + no-callback defaults.

### Verification
- `mvn -DskipITs verify` green with the JaCoCo 80% gate; unit tests cover agent invoke-then-advance,
  best-effort swallow, erasure (happy path, unknown correlation no-op, tenant isolation, validation),
  and the erasure controller's 200/404. New Testcontainers ITs cover `findByBusinessKey`,
  `deleteByBusinessKey`, and `deleteByInstance`.

---

## Phase 3 — Grid Integration Deepening 🔄 (session 6 — closed loop: idempotent intake + outcome callback)

**Commit:** `feat(flow): close the Grid DEFER loop — idempotent intake + correlation-keyed outcome callback`

Phase 2 completed human approval governance. Phase 3 closes the loop with Aether Grid's confidence
gate: a deferral now flows *back* to Grid once a human decides, and a re-delivered deferral is a no-op.

### What was done

**Idempotent deferral intake:**
- `WorkflowInstanceStore.findByBusinessKey(scope, businessKey)` — a scoped, most-recent lookup by the
  instance's business key (added to the JDBC store and the in-memory test fake).
- `DefaultApprovalGateway.accept` now checks for an existing instance under the deferral's
  `correlationId` (the instance business key) before parking a new one. A retried DEFER reuses the
  existing instance and never raises a second review. `DeferredDecision.WORKFLOW_KEY = "grid-deferral"`
  is now the single canonical key for the deferral workflow.

**Correlation-keyed outcome callback (the closing half of the seam):**
- `DeferralOutcome` domain record — the bounded projection reported back to Grid (correlationId,
  tenant, terminal `APPROVED`/`REJECTED`, decider, timestamp); no comment, request internals, or PII.
- `GridOutcomePort` (domain) with a `NO_OP` default; `LoggingGridOutcomeNotifier` (standalone default)
  and the config-gated best-effort `HttpGridOutcomeNotifier` (POSTs the bounded envelope when
  `aether.flow.grid.callback-url` is set).
- `DefaultWorkflowOrchestrationService` fires the callback on **approve** and on the **terminal
  reject** path — but only for instances of the `grid-deferral` workflow (a normal workflow, or a
  reject that routes to a rework branch, reports nothing). Best-effort: a failing callback is logged
  and swallowed — the human decision is already durably persisted.

### Constraints upheld
- The outbound projection carries no Grid internals and no PII — same discipline as the inbound
  `DeferredDecision`.
- Escalation/decision semantics unchanged — Flow still never auto-decides; the callback only *reports*
  a decision a human already made.
- Flow still runs standalone: `NO_OP`/logging defaults mean no Grid need be present.

### Verification
- `mvn -DskipITs verify` green with the JaCoCo 80% gate; new unit tests cover idempotent intake,
  outcome-fired-on-decide (approve + reject), the non-deferral-workflow no-op, and best-effort swallow.

---

## Phase 2 — Human Approval & SLA Governance 🔄 (session 3 — SLA policy, escalation chains, reassignment, notifications)

**Commit:** `feat(flow): per-tenant SLA policy, multi-level escalation chains, reassignment, notifications (V005)`

Phase 1 hardened the orchestration engine; escalation only *flagged* a breached task `ESCALATED`.
Phase 2 turns escalation into a governed, multi-level workflow and adds the reviewer-facing controls.

### What was done

**Per-tenant SLA policy (governance):**
- `SlaPolicy` domain record (SLA budget + ordered escalation chain of roles), `SlaPolicyStore` port +
  `JdbcSlaPolicyStore` (upsert per tenant, chain stored as CSV), and `SlaPolicyController`
  (`GET`/`PUT /api/v1/tenants/{tenantId}/sla-policy`). Business-hours calendars deferred.

**Multi-level escalation chains:**
- `ApprovalTask` gains `escalationLevel` and a chain-aware `escalate(nextRole, newDueAt)` (reassign +
  fresh budget, level bumped) alongside the flag-only `escalate()`.
- `SlaEscalationService` reworked from a set-based `UPDATE` to a policy-driven sweep: it loads
  breached open tasks + each tenant's policy, and routes a task at level *L* to `chain[L]` with a
  fresh budget — climbing role → manager → executive across sweeps; exhausted chain stays `ESCALATED`.
  New `ApprovalTaskStore.findBreachedOpen` + `countOpen` back it. Escalation still never decides.

**Delegation / reassignment:**
- `ApprovalTask.reassign(newRole)` (open-only) + `POST /api/v1/tenants/{tenantId}/approvals/{id}/reassign`
  hand an open task to another role's queue without deciding it.

**Notifications:**
- `ApprovalNotificationPort` (domain) with the dependency-free `LoggingApprovalNotifier` default,
  fired on task **raise** (orchestration engine + Grid deferral gateway) and on **escalation** (the
  sweep). Webhook/email sinks are adapters behind the same port (follow-up). Notification is
  best-effort — a failing sink never breaks raising or the sweep.

**Migration V005** — `tenant_sla_policy` table + `approval_tasks.escalation_level` column (api +
flow-engine test + flow-infra copies). The `approval_tasks` UPSERT now also persists `assigned_role`,
`sla_due_at`, and `escalation_level` so reassignment and escalation survive.

**Tests — 104 unit tests green (was 84):**
- Domain `SlaPolicyTest`, `ApprovalTaskTest` (reassign + chain-escalate cases); engine
  `SlaEscalationServiceTest` (chain routing, exhaustion, empty-chain idempotency, notifier counts);
  api `SlaPolicyControllerTest` + `ApprovalTaskControllerTest` (reassign 200/400/404/409).
- ITs: `JdbcSlaPolicyStoreIT` + a chain-routing case in `SlaEscalationServiceIT`, under failsafe.
- `mvn -DskipITs verify` passes the JaCoCo 80% gate.

### Remaining Phase 2 (follow-up)
- Phase 2 is core complete. Further SLA refinements (holiday calendars, per-workflow overrides) are
  Phase 3+ candidates.

---

## Phase 2 — Human Approval & SLA Governance ✅ (session 5 — business-hours SLA calendars)

**Commit:** `feat(flow): per-tenant business-hours SLA calendars (V006)`

The SLA clock ran on plain wall time: a task raised at 16:55 with a 30-minute budget was "breached"
by the next morning, and escalation could climb the whole chain overnight or across a weekend. This
session makes SLA budgets measure **working time** when a tenant configures a calendar.

### What was done

**Business-hours calendar (domain):**
- `BusinessHours` value type (flow-domain) — `(zone, start, end, workingDays)` with
  `deadlineAfter(from, budgetMinutes)`: advances the budget through working windows only, skipping
  nights, weekends, and non-working days; a start outside the window clocks from the next opening.
  Validated on construction (start < end, at least one working day). `standard(zone)` = Mon–Fri
  09:00–17:00.
- `SlaPolicy` gains an optional `BusinessHours businessHours` (a 3-arg convenience constructor keeps
  old call-sites valid; `null` = 24/7). `SlaPolicy.deadlineFrom(start, budgetMinutes)` is the single
  place a deadline is computed — business-hours-aware when a calendar is set, plain wall-clock
  otherwise — so the initial raise and the escalation reset stay consistent.

**Threaded through both SLA clock points (engine):**
- `SlaEscalationService` resets a fresh budget via `policy.deadlineFrom(now, defaultSlaMinutes)`.
- `DefaultWorkflowOrchestrationService` and `DefaultApprovalGateway` accept an optional
  `SlaPolicyStore`; when present, a raised task's initial deadline is computed against the tenant's
  calendar. Convenience constructors keep the policy store optional (null → wall-clock), so existing
  wiring and tests are unaffected. `ApprovalTask.raise(instance, step, role, slaDueAt)` overload
  carries the computed deadline.

**Persistence + API:**
- `JdbcSlaPolicyStore` reads/writes four nullable columns (`business_zone`, `business_start`,
  `business_end`, `business_days`); NULL across the set = 24/7. **V006** migration adds them (×3:
  flow-api, flow-engine test, flow-infra).
- `SlaPolicyController` accepts an optional `businessHours` block on `PUT` (zone, `HH:mm` start/end,
  `DayOfWeek` names) and returns it on `GET`; incomplete or unparseable calendars → `400` (not `500`).

**Tests — 132 unit tests green (was 118):**
- `BusinessHoursTest` (8): same-day, next-day spill, weekend skip, before-window, non-working-day,
  non-positive budget, `isWithin`. `SlaPolicyTest` (+3): 24/7 default, working-time deadline, clamp.
- Engine: `SlaEscalationServiceTest` (business-hours reset lands in a working window) +
  `DefaultWorkflowOrchestrationServiceTest` (raise deadline inside the window).
  `SlaPolicyControllerTest` (+2): business-hours round-trip view, invalid-calendar 400.
- ITs: `JdbcSlaPolicyStoreIT` (+2) — calendar round-trip + clearing on re-save (upsert), under failsafe.

---

## Phase 2 — Human Approval & SLA Governance 🔄 (session 4 — email notification sink)

**Commit:** `feat(flow): email approval notification sink (best-effort, config-gated)`

The notification seam already fanned a webhook out behind `ApprovalNotificationPort`. This session
adds the second real transport — email — completing the notification follow-up, again without the
engine ever touching a mail API directly.

### What was done

**Email sink:**
- `EmailApprovalNotifier` (flow-engine) — sends a **bounded plain-text message** (event type, task id,
  tenant, instance/workflow/step keys, role, outcome, escalation level, SLA deadline — never a
  decision, comment, or Grid/PII content) via Spring's `MailSender`/`JavaMailSender`. Delivery is
  **best-effort**: any `MailException` is logged and swallowed, so an SMTP outage never breaks task
  raising or the escalation sweep — mirroring the webhook sink.
- `FlowApiConfig.approvalNotificationPort` now also fans an `EmailApprovalNotifier` into the
  `CompositeApprovalNotifier` **only when** `aether.flow.notification.email.to` is set **and** a
  `MailSender` bean is present (injected via `ObjectProvider`, so Flow still boots with no mail
  configured). Sender defaults to `aether-flow@localhost` (`…email.from`). SMTP host/port/credentials
  come from `spring.mail.*` (environment-sourced; no secrets in source).
- `flow-engine` gains a `spring-boot-starter-mail` dependency for `JavaMailSender`.

**Tests — 118 unit tests green (was 113):**
- `EmailApprovalNotifierTest` (5): blank recipient/sender/null-sender rejection, bounded message on
  raise (from/to/subject/body assertions), escalated event subject, and an SMTP failure swallowed on
  both raise + escalate (capturing + failing `MailSender` fakes — no live SMTP).
- No new migration — notification is a side-channel over existing state.

---

## Phase 2 — Human Approval & SLA Governance 🔄 (session 3 — operator metrics)

**Commit:** `feat(flow): operator metrics over the approval lifecycle (Micrometer)`

The escalation sweep already emitted an escalated counter and an open-queue gauge. This session
completes the operator metric set with counters over the human-driven lifecycle, wired through a
framework-free port so the engine never depends on Micrometer.

### What was done

**Approval-lifecycle counters:**
- `ApprovalMetricsPort` (flow-domain) — a framework-free port with `recordRaised/Approved/Rejected/
  Reassigned` and a `NO_OP` default, mirroring the `ApprovalNotificationPort` seam.
- `MicrometerApprovalMetrics` (flow-api) — registers and increments four counters:
  `aether.flow.approvals.{raised,approved,rejected,reassigned}`. Registered at startup so a dashboard
  sees them from zero.
- Wired at the transitions where they happen: `DefaultWorkflowOrchestrationService` (raised on park,
  approved/rejected on decision), `DefaultApprovalGateway` (raised on Grid deferral intake),
  `ApprovalTaskController` (reassigned on delegation). Engine services keep a convenience constructor
  defaulting to `NO_OP` so unit tests and standalone runs need no registry.
- Escalation + open-queue metrics are unchanged (still `aether.flow.escalation.escalated` +
  `aether.flow.approvals.open` in the sweep) — the port deliberately does not duplicate them.

**Tests — 113 unit tests green (was 110):**
- `MicrometerApprovalMetricsTest` (2): per-transition increments over a `SimpleMeterRegistry`,
  counters present from startup.
- Engine `lifecycleTransitionsAreMetered` (raised/approved/rejected counts) + a controller assertion
  that reassignment is metered. No new migration — metrics are a read-side projection of existing state.

---

## Phase 2 — Human Approval & SLA Governance 🔄 (session 2 — webhook notification sink)

**Commit:** `feat(flow): webhook approval notification sink (best-effort, config-gated)`

The notification seam already had a logging default and an `ApprovalNotificationPort` abstraction.
This session adds the first real transport behind it — a webhook — without the engine ever talking
to HTTP directly.

### What was done

**Webhook sink + fan-out:**
- `WebhookApprovalNotifier` (flow-engine) — POSTs a **bounded JSON envelope** (event type, task id,
  tenant, instance/workflow/step keys, role, outcome, escalation level, SLA deadline — never a
  decision, comment, or Grid/PII content) to a configured URL via Spring `RestClient`. Delivery is
  **best-effort**: any transport failure is logged and swallowed, so a dead endpoint never breaks
  task raising or the escalation sweep.
- `CompositeApprovalNotifier` (flow-engine) — fans each signal out to every configured sink, each
  invoked independently and best-effort so one throwing sink never suppresses the others.
- `FlowApiConfig.approvalNotificationPort` now composes the always-on logging sink with a
  `WebhookApprovalNotifier` **only when** `aether.flow.notification.webhook.url` is set (with a
  configurable `…webhook.timeout-seconds`, default 10). Flow still runs standalone on logging alone.
- `flow-engine` gains a `spring-web` dependency for `RestClient` (mirrors memory-engine's peer client).

**Tests — 110 unit tests green (was 104):**
- `WebhookApprovalNotifierTest` (3): blank-URL rejection, unreachable sink swallowed on raise + escalate.
- `CompositeApprovalNotifierTest` (3): fan-out to every sink, throwing sink does not suppress others,
  null delegate list is a no-op.
- No new migration — notification is a side-channel over existing state.

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

---

## Phase 1 — deliverable 4: branching gateways (exclusive) ✅

**Commit:** `feat(flow): exclusive branching gateways via approval-outcome rework routing`

The engine previously followed a strictly linear `nextStepKey` and a reject always terminated the
instance. A `HUMAN_APPROVAL` step can now declare a **`reworkStepKey`**: rejecting it routes the
instance to that rework step (and drives on) instead of stopping — e.g. `intake → review → finish`
with `review` reject → `fix → review`, a genuine non-linear branch with a loop. A reject with no
rework branch still terminates in `REJECTED`.

- `WorkflowStep` gains `reworkStepKey` (7th field; nulled for non-approval steps) +
  `humanApprovalWithRework(...)` factory. `WorkflowDefinition.validateGraph` requires the rework
  target to resolve, so a malformed branch never persists.
- `DefaultWorkflowOrchestrationService.reject` branches to the rework step (resolving the definition
  by the instance's pinned version) and drives on; the `MAX_TRANSITIONS` guard bounds any loop.
- `WorkflowDefinitionController.StepRequest` gains `reworkStepKey`; it flows through create and the
  definition view.

**Deferred (a genuinely larger, separate capability):** parallel (AND) **fork/join** and general
data-condition (XOR-on-variables) gateways. Both need a **multi-token instance model** (an instance
today holds a single `currentStepKey`), so they are tracked for a later phase rather than shoehorned in.

**Tests — 84 unit tests green (was 79):**
- `WorkflowStepTest` — rework field carried on approval gates, nulled elsewhere
- `WorkflowDefinitionVersioningTest` — rework target must resolve; valid rework graph accepted
- `DefaultWorkflowOrchestrationServiceTest` — decisive reject → rework → re-park → approve → complete
- `mvn -DskipITs verify` passes the JaCoCo 80% gate; ITs run under failsafe in CI.

**Phase 1 status:** core deliverables complete (cancellation, ITs-in-CI, versioning, branching);
parallel AND fork/join deferred. Next: Phase 2 — Human Approval & SLA Governance.
