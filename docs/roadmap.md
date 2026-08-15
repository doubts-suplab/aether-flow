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

## Phase 1 — Orchestration Engine Hardening ✅ (core complete; parallel AND fork/join deferred)

**Goal:** The state machine is fully operational end-to-end under integration tests.

| Deliverable | Status |
|---|---|
| Instance cancellation endpoint + operator actions (withdraws open approval task; `GET …/instances/stats`) | ✅ |
| Testcontainers coverage green in CI (definition, instance, approval stores, escalation) — `maven-failsafe-plugin` wired; `*IT` run at `verify` | ✅ |
| Branching gateways (beyond linear step graphs) — exclusive branching via approval-outcome routing (reject → rework branch, with loops) | ✅ |
| Parallel (AND) fork/join + data-condition gateways — deferred: needs a multi-token instance model (tracked for a later phase) | ⏳ |
| Definition versioning — publish new versions; version-pinned execution keeps in-flight instances on their own version | ✅ |

---

## Phase 2 — Human Approval & SLA Governance 🔄 (core complete)

**Goal:** Rich review workflows and escalation policy.

| Deliverable | Status |
|---|---|
| Multi-level escalation chains (role → manager → executive) — chain-driven sweep, fresh budget per level | ✅ |
| Per-tenant SLA policy — budgets + escalation chain (`tenant_sla_policy`, V005), GET/PUT endpoint | ✅ |
| Per-tenant SLA policy — **business-hours calendars** (`BusinessHours` on `SlaPolicy`, V006; SLA budgets consume working time only, applied at raise + escalation) | ✅ |
| Notifications on task raise / escalation — `ApprovalNotificationPort` (logging default) | ✅ |
| Notification sink — **webhook** adapter (`WebhookApprovalNotifier`, config-gated, best-effort) fanned in via `CompositeApprovalNotifier` | ✅ |
| Notification sink — **email** adapter (`EmailApprovalNotifier` over `JavaMailSender`, config-gated, best-effort) fanned in via `CompositeApprovalNotifier` | ✅ |
| Delegation and reassignment of approval tasks (`POST .../approvals/{id}/reassign`) | ✅ |
| Operator metrics — Micrometer counters for the approval lifecycle (`aether.flow.approvals.{raised,approved,rejected,reassigned}`) alongside the existing escalation counter + open-queue gauge | ✅ |

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

---

## Ecosystem review — future backlog

> Repo-specific items from the [ecosystem improvement backlog](https://github.com/doubts-suplab/aether/blob/main/docs/roadmaps/ecosystem-improvements.md). Planned, not started.
> Feasibility: **S** small · **M** moderate · **L** large. License unchanged (AGPL-3.0).

| Item | Feasibility |
|---|---|
| Richer step types + non-linear patterns (parallel AND fork/join) *(explicitly deferred — needs a multi-token instance model)* | M–L |
| Visual/designer UI or BPMN import (if intended) | L |
| More sophisticated escalation chains + notifications *(addressed in Phase 2; escalation chains, webhook + email sinks, and business-hours calendars all delivered)* | M |
| Operator visibility + metrics *(approval-lifecycle counters + escalation/open metrics delivered in Phase 2; richer dashboards remain)* | M |
| Resilience of long-running instances | M |
| Tighter contract with Grid's confidence decisions | M |
