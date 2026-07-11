package com.suplab.aether.flow.domain;

/**
 * A single node in a {@link WorkflowDefinition}'s ordered process graph.
 *
 * <p>The scaffold models a linear process: each step names the {@code nextStepKey} it transitions
 * to when it completes. A {@link StepType#END} step has no successor ({@code nextStepKey} is
 * {@code null}). A {@link StepType#HUMAN_APPROVAL} step carries an {@code slaMinutes} budget — the
 * time a raised {@link ApprovalTask} has before it breaches its SLA and becomes eligible for
 * escalation — and an {@code assignedRole}, the role expected to action the raised task.</p>
 *
 * @param key          unique-within-definition identifier for this step
 * @param name         human-readable label
 * @param type         the kind of work this step represents
 * @param slaMinutes   approval SLA budget in minutes (only meaningful for HUMAN_APPROVAL; 0 otherwise)
 * @param assignedRole role expected to action a raised approval task (defaulted for HUMAN_APPROVAL,
 *                     {@code null} for other step types)
 * @param nextStepKey  the step this transitions to on completion, or {@code null} for an END step
 */
public record WorkflowStep(
        String key,
        String name,
        StepType type,
        int slaMinutes,
        String assignedRole,
        String nextStepKey
) {
    public WorkflowStep {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("step key required");
        if (name == null || name.isBlank()) name = key;
        if (type == null) throw new IllegalArgumentException("step type required");
        if (slaMinutes < 0) throw new IllegalArgumentException("slaMinutes must be >= 0");
        if (type == StepType.HUMAN_APPROVAL && (assignedRole == null || assignedRole.isBlank()))
            assignedRole = "reviewer";
        if (type != StepType.HUMAN_APPROVAL) assignedRole = null;
        if (type.isTerminal() && nextStepKey != null)
            throw new IllegalArgumentException("END step must not declare a nextStepKey");
        if (!type.isTerminal() && (nextStepKey == null || nextStepKey.isBlank()))
            throw new IllegalArgumentException("non-terminal step " + key + " requires a nextStepKey");
    }

    /**
     * Factory for a human-approval gate with an SLA budget and an assigned role.
     */
    public static WorkflowStep humanApproval(String key, String name, int slaMinutes,
                                             String assignedRole, String nextStepKey) {
        return new WorkflowStep(key, name, StepType.HUMAN_APPROVAL, slaMinutes, assignedRole, nextStepKey);
    }

    /**
     * Factory for an automated system step.
     */
    public static WorkflowStep automated(String key, String name, String nextStepKey) {
        return new WorkflowStep(key, name, StepType.AUTOMATED, 0, null, nextStepKey);
    }

    /**
     * Factory for the terminal step.
     */
    public static WorkflowStep end(String key, String name) {
        return new WorkflowStep(key, name, StepType.END, 0, null, null);
    }
}
