# Project Context — Aether Flow

## Service Identity
- **Name:** Aether Flow (`suplab/aether-flow`)
- **Purpose:** Workflow orchestration platform — process orchestration, human approval steps, state persistence, Grid DEFER integration
- **Port:** 8085
- **Database:** `aether_flow` (PostgreSQL 16, separate from Core, Grid, Memory, and Vault)
- **Ecosystem layer:** Platform (above Grid runtime and Core cognition; alongside Memory and Vault; below domain products)

## Capability Ownership
- **Owns (exclusively):** Workflows — Process Orchestration, Human Approval Steps, State Persistence, Integration with Grid
- Personal memory stays in Aether Core; shared memory stays in Aether Memory; documents/knowledge stay in Aether Vault; Flow owns processes and the human decisions that drive them

## Maven Modules
| Module | Artifact ID | Purpose |
|---|---|---|
| `flow-domain` | `flow-domain` | Domain types + port interfaces (no Spring) |
| `flow-engine` | `flow-engine` | JDBC stores, orchestration state machine, Grid DEFER gateway, SLA escalation |
| `flow-api` | `flow-api` | Spring Boot app, REST controllers, Flyway, escalation scheduler |
| `flow-infra` | `flow-infra` | Docker Compose, k8s, standalone migrations |

## Key Packages
- `com.suplab.aether.flow.domain` — WorkflowDefinition, WorkflowStep, StepType, WorkflowInstance, WorkflowStatus, ApprovalTask, ApprovalOutcome, DeferredDecision, FlowScope
- `com.suplab.aether.flow.ports` — WorkflowDefinitionStore, WorkflowInstanceStore, ApprovalTaskStore, WorkflowEnginePort, ApprovalGatewayPort, SlaEscalationPort
- `com.suplab.aether.flow.engine.store` — JdbcWorkflowDefinitionStore, JdbcWorkflowInstanceStore, JdbcApprovalTaskStore
- `com.suplab.aether.flow.engine.orchestration` — DefaultWorkflowOrchestrationService (state machine)
- `com.suplab.aether.flow.engine.gateway` — DefaultApprovalGateway (Grid DEFER intake)
- `com.suplab.aether.flow.engine.escalation` — SlaEscalationService
- `com.suplab.aether.flow.api` — AetherFlowApplication, controllers, config, escalation scheduler

## Environments
- **Local:** Docker Compose at `flow-infra/docker/docker-compose.yml` (postgres-flow on 5436, app on 8085)
- **CI:** GitHub Actions, `postgres:16` service container
- **Production:** Kubernetes (manifests in `flow-infra/k8s/`; Helm chart — planned Phase 4)

## Current Status
- Phase 0 (scaffold) complete — domain, engine, API, infra, docs, CI all in place; 57 unit tests green; fat jar boots
- Next: Phase 1 — Orchestration Engine Hardening
