# Domain Glossary — Aether Flow

| Term | Definition |
|---|---|
| **Workflow Definition** | A versioned process template: an ordered graph of steps with exactly one END. Validated on construction. |
| **Flow Scope** | The `(tenantId, workflowKey)` ownership key. Every store read/write is scoped by it — the multi-tenancy boundary. |
| **Workflow Key** | The stable business key of a workflow (e.g. `invoice-approval`), version-independent. |
| **Workflow Step** | A node in the graph: `AUTOMATED`, `AGENT`, `HUMAN_APPROVAL` (with SLA + assigned role), or `END`. |
| **Step Type** | `AUTOMATED` (advance now) \| `AGENT` (advance now; agent hook later) \| `HUMAN_APPROVAL` (park for a human) \| `END` (complete). |
| **Workflow Instance** | A running (or finished) execution of a definition, pinned to the definition version it started under. |
| **Workflow Status** | `RUNNING` \| `WAITING_APPROVAL` \| `COMPLETED` \| `REJECTED` \| `CANCELLED` \| `FAILED`. |
| **Drive** | Advancing an instance from its current step until it parks at an approval gate or reaches a terminal state — every transition persisted. |
| **Approval Task** | A human review gate raised at a `HUMAN_APPROVAL` step, with an SLA deadline and an assigned role. |
| **Approval Outcome** | `PENDING` \| `APPROVED` \| `REJECTED` \| `ESCALATED`. `PENDING`/`ESCALATED` are open; `APPROVED`/`REJECTED` are decided. |
| **SLA Deadline** | `slaDueAt` on a task, `slaMinutes` after it is raised; an open task past it is *breached*. |
| **SLA Escalation** | A scheduled set-based sweep flagging breached `PENDING` tasks as `ESCALATED`. Raises visibility, never auto-decides. |
| **Deferred Decision** | Grid's bounded DEFER projection: correlation id, tenant, coarse agent id, summary, confidence, requested role. No Grid internals. |
| **Confidence Gate** | Grid's threshold (`0.8`); decisions below it are deferred to a human rather than auto-actioned. |
| **grid-deferral** | The canonical per-tenant single-approval workflow the DEFER gateway routes deferrals into. |

## Ecosystem terms
| Term | Definition |
|---|---|
| **Aether Core** | Personal cognitive engine — owns per-user memory. |
| **Aether Grid** | Distributed agent mesh / API governance runtime — the source of DEFER decisions Flow reviews. |
| **Aether Memory** | Shared team/org memory platform — sibling platform layer. |
| **Aether Vault** | Knowledge platform — sibling platform layer. Flow owns processes, Vault owns knowledge. |
| **Platform layer** | Aether Flow's position — above Grid/Core, alongside Memory and Vault, below domain products. |
| **Standalone guarantee** | Flow boots and runs with no dependency on Core, Grid, Memory, or Vault. |
