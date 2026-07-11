package com.suplab.aether.flow.domain;

/**
 * The kind of work a {@link WorkflowStep} represents.
 *
 * <ul>
 *   <li>AUTOMATED — a system task the engine completes without waiting (advances immediately)</li>
 *   <li>AGENT — an AI-agent task; in this scaffold it advances like an automated step, but is
 *               modelled distinctly so agent invocation can be wired in later</li>
 *   <li>HUMAN_APPROVAL — a human review gate; the instance parks in {@code WAITING_APPROVAL}
 *               until a person decides, and an {@link ApprovalTask} with an SLA is raised</li>
 *   <li>END — the terminal step; reaching it completes the instance</li>
 * </ul>
 */
public enum StepType {
    AUTOMATED,
    AGENT,
    HUMAN_APPROVAL,
    END;

    /**
     * @return {@code true} if a step of this type parks the instance for a human decision.
     */
    public boolean requiresHuman() {
        return this == HUMAN_APPROVAL;
    }

    /**
     * @return {@code true} if reaching a step of this type completes the workflow instance.
     */
    public boolean isTerminal() {
        return this == END;
    }
}
