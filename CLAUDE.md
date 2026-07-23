# CLAUDE.md — Aether Flow Project Brief

> Read this at the start of every session. Single source of truth for what this project is, how it is built, and what rules apply.

---

## What This Project Is

**Aether Flow** (`suplab/aether-flow`) is the **workflow platform** of the Aether ecosystem — the layer that owns **workflow orchestration**: defining BPMN-style multi-step human-AI processes, driving running instances through a persisted state machine, enforcing configurable human approval gates with SLAs and escalation, and receiving DEFER decisions from Aether Grid into approval queues.

> **Ecosystem navigation**
>
> | Layer | Repo | Purpose |
> |---|---|---|
> | Aether Philosophy | [`suplab/aether`](https://github.com/suplab/aether) | The vision: cognitive fabric connecting humans, memory, and AI |
> | **Aether Core** | [`suplab/aether-core`](https://github.com/suplab/aether-core) | Personal cognitive engine — individual memory, reasoning, emotional context |
> | **Aether Grid** | [`suplab/aether-grid`](https://github.com/suplab/aether-grid) | Distributed agent mesh — enterprise API governance platform |
> | **Aether Memory** | [`suplab/aether-memory`](https://github.com/suplab/aether-memory) | Shared team/org memory platform — federation, per-tenant policy |
> | **Aether Vault** | [`suplab/aether-vault`](https://github.com/suplab/aether-vault) | Knowledge platform — document indexing, vector search, RAG, knowledge graph |
> | **Aether Flow** | `suplab/aether-flow` ← **you are here** | Workflow platform — process orchestration, human approval, SLA escalation, Grid DEFER intake |

**Capability owned (exclusively):** *Workflows* — Process Orchestration, Human Approval Steps, State Persistence, Integration with Grid. Flow orchestrates processes; it does not store personal memory (Core), shared memory (Memory), or documents/knowledge (Vault).

**Current status:** Phase 1 — Orchestration Engine Hardening 🔄 (in progress, 2 of 4): instance cancellation (withdraws the open approval task) + operator stats endpoints and Testcontainers ITs wired into CI are done; parallel/branching gateways and definition versioning + in-flight migration remain.

**One runnable application:**
- `flow-api` — Workflow Platform API (port 8085)

**Three library modules:** `flow-domain`, `flow-engine`, `flow-infra`

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 (`jakarta.*` exclusively — never `javax.*`) |
| Database | PostgreSQL 16 |
| Orchestration | Persisted state-machine engine (no BPMN runtime dependency — plain records + JDBC) |
| Scheduling | Spring `@Scheduled` SLA escalation sweep |
| DB Migrations | Flyway (classpath:db/migration in flow-api) |
| Build | Maven (multi-module, Java 21, --enable-preview) |
| Local Dev | Docker Compose (`flow-infra/docker/docker-compose.yml`) |
| CI/CD | GitHub Actions (OIDC, SHA-pinned actions) |

> Aether Flow deliberately owns **no** vector store, embedding model, or LLM runtime — orchestration is not a semantic capability. It integrates with Grid over REST at the DEFER seam.

---

## Bounded Context

- Package root: `com.suplab.aether.flow`
- Port: **8085** (Grid proxy=8080, Grid api=8081, Core=8082, Memory=8083, Vault=8084, Flow=8085)
- Database: `aether_flow` (separate schema — data isolation from Core, Grid, Memory, and Vault)
- REST API surface:
  - `.../tenants/{tenantId}/workflows` — workflow definition CRUD
  - `.../tenants/{tenantId}/workflows/{workflowKey}/instances` — start / query running instances
  - `.../tenants/{tenantId}/approvals` — human review queue + approve/reject
  - `POST /api/v1/deferrals` — Aether Grid DEFER intake

---

## Module Structure

```
aether-flow-parent (pom.xml)
├── flow-domain   — domain types (WorkflowDefinition, WorkflowInstance, ApprovalTask, DeferredDecision, FlowScope) + port interfaces
├── flow-engine   — JDBC stores, orchestration state machine, Grid DEFER gateway, SLA escalation service
├── flow-api      — Spring Boot REST API, Flyway migrations, config, escalation scheduler
└── flow-infra    — Docker Compose, k8s manifests, migration reference copies (no Java sources)
```

### Dependency Graph

```
flow-api
  ├── flow-domain
  └── flow-engine
        └── flow-domain
flow-infra  (no Java)
```

`flow-domain` has no framework dependency — pure Java 21 records and interfaces.

---

## Core Domain Concepts

| Concept | Meaning |
|---|---|
| **WorkflowDefinition** | A versioned process template: an ordered graph of steps with exactly one END. Validated on construction. |
| **WorkflowStep** | A node in the graph — `AUTOMATED`, `AGENT`, `HUMAN_APPROVAL`, or `END`. Approval steps carry an SLA budget and an assigned role. |
| **WorkflowInstance** | A running (or finished) execution, pinned to a definition version. Its persisted state is what survives restarts. |
| **FlowScope** | The `tenantId` + `workflowKey` ownership key — the multi-tenancy boundary. |
| **ApprovalTask** | A human review gate raised at a `HUMAN_APPROVAL` step, with an SLA deadline. Approve / reject / escalate; withdrawn if its instance is cancelled. |
| **DeferredDecision** | The bounded projection Aether Grid sends when its confidence gate defers a decision to a human. |
| **SLA Escalation** | A scheduled sweep that flags breached `PENDING` tasks as `ESCALATED` — raises visibility, never auto-decides. |

---

## Pre-Coding Checklist

Before writing any code:
- [ ] Which module does this change belong to? Does it respect bounded context?
- [ ] Is there an existing port interface or utility to reuse?
- [ ] Does this change require a new Flyway migration?
- [ ] Does this change affect the data model or API contract? → update `docs/architecture.md`
- [ ] Does this change affect the roadmap status? → update `docs/progress.md` and `docs/roadmap.md`
- [ ] Does this touch the Grid seam? → is the decision projection still bounded (no Grid internals imported)?

---

## Ten Golden Rules (Non-Negotiable)

1. **Constructor injection exclusively** — no field-level `@Autowired`, no `@Inject`, fields must be `final`
2. **No hardcoded secrets** — all credentials to environment variables; never committed to source
3. **SLF4J with parameterized messages** — never `System.out.println()` or string concatenation in logs
4. **SOLID design principles** — single responsibility, open/closed, Liskov, interface segregation, dependency inversion
5. **DDD bounded contexts** — cross-module calls go through port interfaces, never reach into another module's internals
6. **Explicit column lists in SQL** — never `SELECT *`; always name every column
7. **Parameterized queries only** — no string concatenation for SQL; use `NamedParameterJdbcTemplate`
8. **Conventional Commits** — `type(scope): description` (feat, fix, docs, chore, build, test, refactor)
9. **No `// TODO` in committed code** — if it's not done, don't commit it
10. **`jakarta.*` exclusively** — Spring Boot 3.x; `javax.*` imports are a build-breaking error

### Aether Flow-Specific Constraints

- All workflow, instance, and approval queries scoped by `tenant_id` (and `workflow_key` where applicable) — no cross-tenant read path
- A `WorkflowDefinition` is validated on construction: exactly one END step, unique step keys, every transition resolvable — malformed processes never persist
- Every state transition is persisted — workflow state must survive a service restart (the *State Persistence* capability)
- Escalation **marks** breached tasks `ESCALATED`; it never approves, rejects, or deletes — a human always decides
- The Grid DEFER projection (`DeferredDecision`) carries no Grid internals, no PII, no raw request payloads — only a bounded summary + coarse provenance
- Confidence < 0.8 is Grid's gate — Flow receives what Grid defers; it never auto-decides a deferral
- Flow is a *platform* layer — it must run standalone without Core, Grid, Memory, or Vault present

---

## Slash Commands

| Command | Purpose |
|---|---|
| `/estimate` | P50/P80/P90 effort estimate (Human Days = Raw Hours / 6.4) |
| `/review` | Code review against golden rules |
| `/adr` | Create an Architecture Decision Record |
| `/security-scan` | Security review of current changes |
| `/memory-update` | Update `.claude/memory/` files after major decisions |

---

## Memory Files

| File | Contents |
|---|---|
| `project-context.md` | Service details, ports, environments |
| `domain-glossary.md` | Aether Flow terminology |
| `decisions.md` | Architecture decisions log |
| `constraints.md` | Hard constraints + golden rules |
| `patterns.md` | Approved patterns in use |
| `session-log.md` | Rolling session log |

---

## Prohibited Patterns

- `javax.*` in any Spring Boot 3.x file
- Field `@Autowired` or `@Inject`
- `SELECT *` in any SQL
- Hardcoded passwords, tokens, or connection strings
- `Thread.sleep()` in tests (use Awaitility or Testcontainers)
- Empty `catch` blocks
- `Optional.get()` without guard
- `System.out.println()` in any production code
- Cross-tenant data access (missing `tenant_id` in WHERE clause)
- Auto-deciding a human approval gate or a Grid deferral (escalation raises visibility only)

---

## Documentation Sync Rule

Every commit that changes system behavior MUST update:
- `docs/progress.md` — mark completed deliverables
- `README.md` — if architecture or scope changed
- `docs/index.html` — if conceptual overview or tech stack changed
- `docs/roadmap.md` — if milestones shift
- `docs/architecture.md` — if architectural decisions change
