# Architecture Decisions — Aether Flow

> Chronological log of significant decisions. Promote any that need full rationale to an ADR under `docs/adr/`.

## ADR-0001 — Standalone platform, mirroring the ecosystem structure (Phase 0)
- **Decision:** Bootstrap Aether Flow as an independent Maven multi-module project (`flow-domain`, `flow-engine`, `flow-api`, `flow-infra`) mirroring the shape and conventions of `aether-memory` and `aether-vault`.
- **Rationale:** The ecosystem standardises on Java 21 / Spring Boot 3.3 / PostgreSQL 16 / Flyway. Consistency makes the platform legible, reviewable, and integrable. Divergent stacks were rejected for fragmenting the ecosystem.
- **Consequence:** Reuses the ecosystem's golden rules, CI patterns, and Docker/k8s patterns.

## ADR-0002 — Workflow as the ownership unit; FlowScope = (tenantId, workflowKey) (Phase 0)
- **Decision:** Workflows are owned by a `workflowKey` within a tenant, keyed by `FlowScope`. Every query is scoped by both.
- **Rationale:** A workflow definition is the natural process boundary — the Flow analogue of Memory's team and Vault's collection. It preserves strict multi-tenant isolation while grouping a definition, its instances, and their approvals.

## ADR-0003 — No BPMN engine, no vector store, no LLM runtime (Phase 0)
- **Decision:** Model the process as plain Java records (`WorkflowDefinition` + `WorkflowStep`) driven by a hand-written state machine over JDBC. No Camunda/Flowable/Activiti dependency; no pgvector; no Ollama.
- **Rationale:** Orchestration is a state-management capability, not a semantic one. A heavyweight BPMN runtime would dominate the scaffold and obscure the domain; a vector store would be dead weight. Keeping Flow single-store on PostgreSQL preserves the "boots standalone" guarantee. A richer process language (parallel/branching gateways) is a later concern behind the same ports.

## ADR-0004 — Definitions validated on construction; malformed graphs never persist (Phase 0)
- **Decision:** `WorkflowDefinition`'s compact constructor rejects any graph without exactly one END step, with duplicate step keys, or with an unresolvable `nextStepKey`.
- **Rationale:** A broken process should fail at definition time (HTTP 400), never at run time mid-instance. Validation in the domain record means every path that builds a definition inherits the guarantee.

## ADR-0005 — Every transition persisted (State Persistence capability) (Phase 0)
- **Decision:** `DefaultWorkflowOrchestrationService` saves the instance after every step transition, park, and completion; instances are immutable records whose transitions return new values.
- **Rationale:** "Workflow state survives service restarts" is an owned capability, not an aspiration. Persisting each transition makes a restarted service resume any instance from its last saved step.

## ADR-0006 — Escalation marks, never decides (Phase 0)
- **Decision:** `SlaEscalationService.sweep` is a single set-based `UPDATE` moving breached `PENDING` tasks to `ESCALATED`; the task stays open and a human must still decide it.
- **Rationale:** Escalation raises visibility on a late review; auto-deciding would defeat the human-in-the-loop purpose of an approval gate. Mirrors Vault's freshness sweep (marks, never deletes).

## ADR-0007 — Grid DEFER intake via a bounded projection and a canonical workflow (Phase 0)
- **Decision:** Grid posts a bounded `DeferredDecision` (correlation id, tenant, coarse agent id, summary, confidence, requested role — no request internals, no PII) to `/api/v1/deferrals`. `DefaultApprovalGateway` maps it to a per-tenant canonical `grid-deferral` workflow, parked at a single approval gate routed to the requested role.
- **Rationale:** Flow must not import Grid internals — the seam is one-directional (Grid → Flow) and privacy-preserving, echoing Memory's federation boundary. Reusing one canonical workflow keeps deferrals in the same queue, lifecycle, and stores as any other Flow process. Per-decision role is honoured by raising the task with the decision's role rather than the shared definition's default.
- **Revisit trigger:** an outcome callback to Grid (closing the loop) and idempotent dedupe by correlation id are Phase 3 concerns.

## ADR-0008 — Cancellation withdraws the open task via a distinct WITHDRAWN outcome (Phase 1)
- **Decision:** Cancelling a non-terminal instance closes its open approval task with a new terminal `ApprovalOutcome.WITHDRAWN` — not by reusing `REJECTED`, and not by deleting the row.
- **Rationale:** `REJECTED` means "a human rejected this approval"; a cancellation is neither an approval decision nor a human verdict on the task, so overloading it would corrupt the audit trail. Deleting the task would lose the record that a gate was raised. `WITHDRAWN` is honest: closed without a decision, excluded from the open queue (`isOpen()` stays PENDING/ESCALATED) and from the human-decided set (`isDecided()` stays APPROVED/REJECTED). Migration `V004` extends the `outcome` CHECK constraint.
- **Consequence:** the escalation sweep (PENDING-only) and the review queue never resurface a withdrawn task; approve/reject on a withdrawn task fails the `requireOpen()` guard (→ 409).
