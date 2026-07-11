# Aether Flow

> Workflow orchestration — the platform layer that defines multi-step human-AI processes, drives them through a persisted state machine, enforces human approval gates with SLAs, and turns Aether Grid's DEFER decisions into review queues, all scoped per tenant.

**Aether Flow** is the workflow platform of the [Aether ecosystem](https://github.com/suplab/aether). Where [Aether Core](https://github.com/suplab/aether-core) owns *personal* memory, [Aether Memory](https://github.com/suplab/aether-memory) owns *shared team* memory, and [Aether Vault](https://github.com/suplab/aether-vault) owns *knowledge*, Aether Flow owns **workflows**: the long-running, human-in-the-loop processes that decide what happens when an agent isn't confident enough to act alone.

**Ecosystem position:** Aether Flow is a **platform layer** — it sits above the runtime (Grid) and cognitive (Core) layers and is consumed by higher-level products (e.g. Aether Enterprise). It runs standalone; Core, Grid, Memory, and Vault are not required to be present.

---

## Quick Start

```bash
cd flow-infra/docker && docker compose up -d
cd ../.. && mvn spring-boot:run -pl flow-api
# Flow API: http://localhost:8085
# Health:   http://localhost:8085/actuator/health
```

## Modules

| Module | Purpose |
|---|---|
| `flow-domain` | Domain types: WorkflowDefinition, WorkflowStep, WorkflowInstance, ApprovalTask, DeferredDecision, FlowScope, port interfaces |
| `flow-engine` | JDBC stores, orchestration state machine, Grid DEFER gateway, SLA escalation sweep |
| `flow-api` | Spring Boot REST API (port 8085) + Flyway migrations + escalation scheduler |
| `flow-infra` | Docker Compose, Kubernetes manifests, standalone Flyway migrations |

## Key API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/tenants/{tenantId}/workflows` | Register a workflow definition (step graph) |
| `GET` | `/api/v1/tenants/{tenantId}/workflows` | List definitions |
| `GET` | `/api/v1/tenants/{tenantId}/workflows/{workflowKey}` | Fetch the active definition |
| `DELETE` | `/api/v1/tenants/{tenantId}/workflows/{workflowKey}` | Delete a definition |
| `POST` | `/api/v1/tenants/{tenantId}/workflows/{workflowKey}/instances` | Start an instance (drives to first stable state) |
| `GET` | `/api/v1/tenants/{tenantId}/workflows/{workflowKey}/instances?status=` | List instances by status |
| `GET` | `/api/v1/tenants/{tenantId}/workflows/{workflowKey}/instances/{id}` | Fetch an instance |
| `GET` | `/api/v1/tenants/{tenantId}/approvals?role=` | The human review queue (open tasks for a role) |
| `POST` | `/api/v1/tenants/{tenantId}/approvals/{taskId}/approve` | Approve — advance the instance past the gate |
| `POST` | `/api/v1/tenants/{tenantId}/approvals/{taskId}/reject` | Reject — stop the instance |
| `POST` | `/api/v1/deferrals` | Aether Grid DEFER intake → human-approval workflow |
| `GET` | `/actuator/health` | Liveness + readiness probes |

## Workflow Model

A **workflow** (`tenantId` + `workflowKey`) is a versioned process template — an ordered graph of steps. An **instance** is a running execution of it; its persisted state is what a restarted service resumes from.

| Concept | Description |
|---|---|
| `WorkflowDefinition` | Versioned step graph, validated on construction (exactly one END, unique keys, resolvable transitions) |
| `WorkflowStep` | `AUTOMATED` · `AGENT` · `HUMAN_APPROVAL` (SLA + role) · `END` |
| `WorkflowInstance` | Lifecycle: `RUNNING → WAITING_APPROVAL → COMPLETED / REJECTED / CANCELLED / FAILED` |
| `ApprovalTask` | A human review gate with an SLA deadline: `PENDING → APPROVED / REJECTED / ESCALATED` |
| `DeferredDecision` | Grid's bounded DEFER projection (correlation id, tenant, agent, summary, confidence) |

### Orchestration

Starting an instance drives it through automated and agent steps until it either **parks** at a `HUMAN_APPROVAL` gate (raising an `ApprovalTask`) or **completes** at the `END` step. A human decision resumes a parked instance: an approval advances it to the next step (and onward until the next park or completion); a rejection stops it. Every transition is persisted, so state survives a restart.

### Human Approval & SLA Escalation

Each approval gate carries an `slaMinutes` budget and an assigned role. A scheduled sweep (default every 5 minutes) flags every `PENDING` task past its deadline as `ESCALATED`, surfacing it at the top of the review queue. Escalation **raises visibility — it never auto-decides**; a human must still approve or reject.

### Grid Integration (DEFER → Approval)

When Aether Grid's confidence gate (`confidence < 0.8`) defers an agent decision to a human, it POSTs a bounded `DeferredDecision` to `/api/v1/deferrals`. Flow's gateway turns it into a workflow instance parked at a human-approval gate on a canonical `grid-deferral` process, routed to the role the decision requested. The projection carries no Grid internals — only a summary and coarse provenance.

## Ecosystem

```
Aether Ecosystem
├── aether          (suplab/aether)         — philosophy, standards, ADRs
├── aether-core     (suplab/aether-core)    — personal cognitive engine (port 8082)
├── aether-grid     (suplab/aether-grid)    — enterprise agent mesh (ports 8080/8081)
├── aether-memory   (suplab/aether-memory)  — shared team memory platform (port 8083)
├── aether-vault    (suplab/aether-vault)   — knowledge platform (port 8084)
└── aether-flow     (suplab/aether-flow)    ← you are here — workflow platform (port 8085)
```

Aether Flow owns the **Workflows** capability exclusively. Memory stays in Core/Memory, knowledge stays in Vault; Flow owns processes and the human decisions that drive them.

---

## Configuration

| Environment Variable | Default | Description |
|---|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/aether_flow` | PostgreSQL connection |
| `POSTGRES_USER` | `aether` | DB username |
| `POSTGRES_PASSWORD` | `aether` | DB password |
| `FLOW_DEFERRAL_SLA_MINUTES` | `60` | SLA budget for a Grid deferral's approval task |
| `FLOW_ESCALATION_ENABLED` | `true` | Toggle the scheduled SLA escalation sweep |
| `FLOW_ESCALATION_CRON` | `0 */5 * * * *` | Escalation sweep schedule |
| `SERVER_PORT` | `8085` | HTTP port |
