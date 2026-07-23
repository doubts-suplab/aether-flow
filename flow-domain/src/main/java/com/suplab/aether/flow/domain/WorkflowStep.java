package com.suplab.aether.flow.domain;

/**
 * A single node in a {@link WorkflowDefinition}'s process graph.
 *
 * <p>Each step names the {@code nextStepKey} it transitions to when it completes (its <em>approve /
 * happy path</em>). A {@link StepType#END} step has no successor ({@code nextStepKey} is
 * {@code null}). A {@link StepType#HUMAN_APPROVAL} step carries an {@code slaMinutes} budget — the
 * time a raised {@link ApprovalTask} has before it breaches its SLA and becomes eligible for
 * escalation — an {@code assignedRole} (the role expected to action the raised task), and an
 * optional {@code reworkStepKey}. When a human <em>rejects</em> an approval step that declares a
 * {@code reworkStepKey}, the instance branches to that step (a rework loop) instead of stopping in
 * {@code REJECTED} — so the graph is no longer strictly linear. A reject with no {@code reworkStepKey}
 * still terminates the instance.</p>
 *
 * @param key           unique-within-definition identifier for this step
 * @param name          human-readable label
 * @param type          the kind of work this step represents
 * @param slaMinutes    approval SLA budget in minutes (only meaningful for HUMAN_APPROVAL; 0 otherwise)
 * @param assignedRole  role expected to action a raised approval task (defaulted for HUMAN_APPROVAL,
 *                      {@code null} for other step types)
 * @param nextStepKey   the step this transitions to on completion / approval, or {@code null} for END
 * @param reworkStepKey the step a rejected approval routes to (a rework branch); {@code null} means a
 *                      reject terminates the instance. Only meaningful for HUMAN_APPROVAL.
 */
public record WorkflowStep(
        String key,
        String name,
        StepType type,
        int slaMinutes,
        String assignedRole,
        String nextStepKey,
        String reworkStepKey
) {
    public WorkflowStep {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("step key required");
        if (name == null || name.isBlank()) name = key;
        if (type == null) throw new IllegalArgumentException("step type required");
        if (slaMinutes < 0) throw new IllegalArgumentException("slaMinutes must be >= 0");
        if (type == StepType.HUMAN_APPROVAL && (assignedRole == null || assignedRole.isBlank()))
            assignedRole = "reviewer";
        if (type != StepType.HUMAN_APPROVAL) {
            assignedRole = null;
            reworkStepKey = null; // rework routing is an approval-gate concept only
        }
        if (reworkStepKey != null && reworkStepKey.isBlank()) reworkStepKey = null;
        if (type.isTerminal() && nextStepKey != null)
            throw new IllegalArgumentException("END step must not declare a nextStepKey");
        if (!type.isTerminal() && (nextStepKey == null || nextStepKey.isBlank()))
            throw new IllegalArgumentException("non-terminal step " + key + " requires a nextStepKey");
    }

    /**
     * Factory for a human-approval gate with an SLA budget and an assigned role (no rework branch —
     * a reject terminates the instance).
     */
    public static WorkflowStep humanApproval(String key, String name, int slaMinutes,
                                             String assignedRole, String nextStepKey) {
        return new WorkflowStep(key, name, StepType.HUMAN_APPROVAL, slaMinutes, assignedRole, nextStepKey, null);
    }

    /**
     * Factory for a human-approval gate whose <em>reject</em> routes to a rework branch
     * ({@code reworkStepKey}) instead of stopping the instance.
     */
    public static WorkflowStep humanApprovalWithRework(String key, String name, int slaMinutes,
                                                       String assignedRole, String nextStepKey,
                                                       String reworkStepKey) {
        return new WorkflowStep(key, name, StepType.HUMAN_APPROVAL, slaMinutes, assignedRole, nextStepKey,
                reworkStepKey);
    }

    /**
     * Factory for an automated system step.
     */
    public static WorkflowStep automated(String key, String name, String nextStepKey) {
        return new WorkflowStep(key, name, StepType.AUTOMATED, 0, null, nextStepKey, null);
    }

    /**
     * Factory for the terminal step.
     */
    public static WorkflowStep end(String key, String name) {
        return new WorkflowStep(key, name, StepType.END, 0, null, null, null);
    }
}
