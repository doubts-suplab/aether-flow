# Session Log — Aether Flow

> Rolling log of working sessions. Newest first.

## Session 1 — Phase 0 Scaffold
- Bootstrapped the standalone `aether-flow` workflow platform mirroring `aether-memory` / `aether-vault` structure and quality bar.
- Created 4 modules: `flow-domain`, `flow-engine`, `flow-api`, `flow-infra`.
- Domain: WorkflowDefinition, WorkflowStep, StepType, WorkflowInstance, WorkflowStatus, ApprovalTask, ApprovalOutcome, DeferredDecision, FlowScope + 6 ports (definition/instance/task stores, engine, gateway, escalation).
- Engine: DefaultWorkflowOrchestrationService (persisted state machine), DefaultApprovalGateway (Grid DEFER intake), SlaEscalationService (set-based sweep), JdbcWorkflowDefinitionStore (JSONB steps), JdbcWorkflowInstanceStore, JdbcApprovalTaskStore.
- API: AetherFlowApplication (8085), WorkflowDefinition/WorkflowInstance/ApprovalTask/DeferredDecision controllers, escalation scheduler + config, application.yml, Dockerfile.
- Infra: Flyway V001–V003 (workflow_definitions, workflow_instances, approval_tasks), docker-compose (postgres on 5436), k8s (namespace/deployment/service+HPA).
- Tests: 57 unit tests green (`mvn verify`); Testcontainers ITs authored for CI (definition/instance/task stores, escalation sweep). Fat jar boots — Spring context wiring validated.
- Docs: CLAUDE.md, README, aether.manifest.yaml, docs/{index.html, architecture.md, roadmap.md, progress.md}, .claude/{memory,agents}.
- CI: ci.yml, quality-gate.yml, docker-build.yml (SHA-pinned actions, OIDC).
- Design choice: no BPMN engine, no vector store, no LLM runtime — orchestration is state management. Key decisions logged as ADR-0001..0007 in decisions.md.
