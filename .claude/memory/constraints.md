# Constraints — Aether Flow

## Ten Golden Rules (Non-Negotiable)
1. Constructor injection exclusively — no field `@Autowired`/`@Inject`; fields `final`
2. No hardcoded secrets — credentials via environment variables only
3. SLF4J parameterized logging — never `System.out.println()` or string concatenation
4. SOLID design principles
5. DDD bounded contexts — cross-module calls go through port interfaces
6. Explicit column lists in SQL — never `SELECT *`
7. Parameterized queries only — `NamedParameterJdbcTemplate`, never string concatenation
8. Conventional Commits — `type(scope): description`
9. No `// TODO` in committed code
10. `jakarta.*` exclusively — `javax.*` is a build-breaking error

## Aether Flow-Specific Hard Constraints
- **Scoping:** every definition, instance, and approval query includes `tenant_id` (and `workflow_key` where the scope demands it) in WHERE — no cross-tenant read path
- **Definition validity:** a `WorkflowDefinition` is validated on construction (exactly one END, unique step keys, resolvable transitions) — a malformed process never persists
- **State persistence:** every instance transition is persisted — a restarted service must resume instances from their last saved step
- **Escalation marks, never decides:** the sweep only transitions breached `PENDING → ESCALATED`; a human always approves or rejects
- **No auto-decide:** an approval gate or a Grid deferral is never resolved by the engine — human-in-the-loop is structural
- **Grid seam is one-directional & bounded:** Flow accepts a `DeferredDecision` from Grid (Grid → Flow); the projection carries no Grid internals, no PII, no raw payloads
- **No semantic store:** Flow owns no vector store, embedding, or LLM runtime — orchestration is state management, not cognition
- **Standalone:** must boot, migrate, serve, and run its escalation sweep with no dependency on Core, Grid, Memory, or Vault
- **Ports:** Grid proxy=8080, Grid api=8081, Core=8082, Memory=8083, Vault=8084, Flow=**8085** — do not collide

## Prohibited Patterns
- `javax.*`, field injection, `SELECT *`, hardcoded credentials
- `Thread.sleep()` in tests (use Testcontainers/Awaitility)
- Empty `catch` blocks, `Optional.get()` without guard
- Missing `tenant_id` in a WHERE clause
- Auto-deciding a human approval gate or a Grid deferral
