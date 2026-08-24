package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStep;

/**
 * Invokes an AI agent when a workflow reaches an {@code AGENT} step — Flow's outbound half of the
 * agent seam with Aether Grid.
 *
 * <p>An {@code AGENT} step models AI-agent work inside a process. The orchestration engine invokes
 * this port as it passes such a step, then advances to the step's successor <em>regardless of the
 * outcome</em> — an agent step is best-effort augmentation, never a park or a gate. The bounded
 * projection passed to an adapter (tenant, workflow key, business key, step key/name) carries no
 * request internals and no PII, mirroring the discipline of the DEFER seam.</p>
 *
 * <p>Implementations must be <strong>best-effort</strong>: a failing or unreachable agent must not
 * break workflow progression. The {@link #NO_OP} default keeps Flow standalone — an agent step simply
 * advances like an automated one when no agent is wired.</p>
 */
public interface AgentStepInvoker {

    /** Invokes the agent for {@code step} on {@code instance}. Implementations must be best-effort. */
    void invoke(WorkflowInstance instance, WorkflowStep step);

    /** A no-op invoker — an AGENT step advances like an automated step with no agent wired. */
    AgentStepInvoker NO_OP = (instance, step) -> { };
}
