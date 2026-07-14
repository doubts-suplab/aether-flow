# Aether Flow — Development Roadmap

> **Scope:** This roadmap covers Aether Flow only.
> For the ecosystem roadmap see [suplab/aether](https://github.com/suplab/aether).

---

## Phase 0 — Scaffold ✅

**Goal:** Standalone platform bootstrapped. Independent Maven multi-module, Spring Boot 3.3.5, all golden rules enforced, ecosystem relationship established.

| Deliverable | Status |
|---|---|
| Independent parent POM (`aether-flow-parent`) | ✅ |
| 4 Maven modules: flow-domain, flow-engine, flow-api, flow-infra | ✅ |
| Domain model: WorkflowDefinition, WorkflowStep, StepType, WorkflowInstance, WorkflowStatus, ApprovalTask, ApprovalOutcome, DeferredDecision, FlowScope | ✅ |
| Port interfaces: WorkflowDefinitionStore, WorkflowInstanceStore, ApprovalTaskStore, WorkflowEnginePort, ApprovalGatewayPort, SlaEscalationPort | ✅ |
| `DefaultWorkflowOrchestrationService` (persisted state machine: start / advance / approve / reject) | ✅ |
| `DefaultApprovalGateway` (Grid DEFER → canonical human-approval workflow, per-decision role) | ✅ |
| `SlaEscalationService` (set-based breached-task sweep) | ✅ |
| JDBC stores: definitions (JSONB step graph), instances, approval tasks | ✅ |
| REST: Definition, Instance, Approval, Deferral controllers + escalation scheduler | ✅ |
| Flyway migrations V001–V003 | ✅ |
| Docker Compose + Kubernetes manifests | ✅ |
| GitHub Actions CI + quality-gate + docker-build | ✅ |
| CLAUDE.md + .claude/memory/ + .claude/agents/ | ✅ |
| Docs: README, index.html, architecture.md, roadmap.md, progress.md | ✅ |

---

## Phase 1 — Orchestration Engine Hardening

**Goal:** The state machine is fully operational end-to-end under integration tests.

| Deliverable | Status |
|---|---|
| Instance cancellation endpoint + operator actions (withdraws open approval task) | ✅ |
| Testcontainers coverage green in CI (definition, instance, approval stores, escalation) | ⏳ |
| Parallel / branching gateways (beyond linear step graphs) | ⏳ |
| Definition versioning + migration of in-flight instances | ⏳ |

---

## Phase 2 — Human Approval & SLA Governance

**Goal:** Rich review workflows and escalation policy.

| Deliverable | Status |
|---|---|
| Multi-level escalation chains (role → manager → executive) | ⏳ |
| Per-tenant SLA policy (budgets, business-hours calendars) | ⏳ |
| Notifications on task raise / breach (webhook, email) | ⏳ |
| Delegation and reassignment of approval tasks | ⏳ |

---

## Phase 3 — Grid Integration Deepening

**Goal:** A closed loop with Aether Grid's confidence gate.

| Deliverable | Status |
|---|---|
| Outcome callback to Grid on deferral decision (correlation-keyed) | ⏳ |
| Agent-step execution (invoke a Grid agent from an `AGENT` step) | ⏳ |
| Idempotent deferral intake (dedupe by correlation id) | ⏳ |
| GDPR erasure across instances and approval history | ⏳ |

---

## Phase 4 — Kubernetes + Helm

**Goal:** Production-ready deployment.

| Deliverable | Status |
|---|---|
| Multi-stage Dockerfile (Temurin 21 JRE, non-root uid 1000) | ✅ (scaffolded) |
| Helm chart `flow-infra/helm/aether-flow/` | ⏳ |
| HPA (min 2, max 8 replicas) | ✅ (manifest) |
| Docker build + Helm release workflows | ⏳ |
