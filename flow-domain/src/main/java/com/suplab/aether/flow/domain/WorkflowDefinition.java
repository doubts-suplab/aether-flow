package com.suplab.aether.flow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A versioned process template owned by a tenant.
 *
 * <p>A definition is an ordered graph of {@link WorkflowStep steps} identified by a stable
 * {@code workflowKey} (the business key) and an integer {@code version}. Running executions are
 * {@link WorkflowInstance}s created from a definition. The scaffold enforces a well-formed linear
 * process: a non-empty step list, unique step keys, exactly one {@link StepType#END} step, and
 * every {@code nextStepKey} resolving to a declared step. The first step in the list is the
 * {@link #startStep() start}.</p>
 *
 * <p>All fields are immutable; the step list is defensively copied and unmodifiable.</p>
 *
 * @param id          stable identifier
 * @param tenantId    owning tenant (isolation boundary)
 * @param workflowKey stable business key, unique per tenant (version-independent)
 * @param name        human-readable name
 * @param version     definition version (>= 1)
 * @param steps       ordered process steps; the first is the start step
 * @param active      whether new instances may be started from this definition
 * @param createdAt   when the definition was registered
 * @param updatedAt   when the definition record last changed
 */
public record WorkflowDefinition(
        UUID id,
        String tenantId,
        String workflowKey,
        String name,
        int version,
        List<WorkflowStep> steps,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public WorkflowDefinition {
        if (id == null) id = UUID.randomUUID();
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (workflowKey == null || workflowKey.isBlank())
            throw new IllegalArgumentException("workflowKey required");
        if (name == null || name.isBlank()) name = workflowKey;
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        if (steps == null || steps.isEmpty()) throw new IllegalArgumentException("steps required");
        steps = List.copyOf(steps);
        validateGraph(steps);
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    private static void validateGraph(List<WorkflowStep> steps) {
        long endCount = steps.stream().filter(s -> s.type().isTerminal()).count();
        if (endCount != 1) throw new IllegalArgumentException("definition must have exactly one END step");
        var keys = steps.stream().map(WorkflowStep::key).distinct().toList();
        if (keys.size() != steps.size()) throw new IllegalArgumentException("step keys must be unique");
        for (WorkflowStep step : steps) {
            if (step.nextStepKey() != null && keys.stream().noneMatch(k -> k.equals(step.nextStepKey())))
                throw new IllegalArgumentException(
                        "step " + step.key() + " points to unknown step " + step.nextStepKey());
        }
    }

    /**
     * Factory for a newly registered, active definition at version 1: random ID, timestamps now.
     */
    public static WorkflowDefinition create(FlowScope scope, String name, List<WorkflowStep> steps) {
        var now = Instant.now();
        return new WorkflowDefinition(UUID.randomUUID(), scope.tenantId(), scope.workflowKey(), name, 1,
                steps, true, now, now);
    }

    /**
     * Returns the owning scope ({@code tenantId} + {@code workflowKey}) of this definition.
     */
    public FlowScope scope() {
        return new FlowScope(tenantId, workflowKey);
    }

    /**
     * @return the start step — the first step in declaration order.
     */
    public WorkflowStep startStep() {
        return steps.get(0);
    }

    /**
     * Resolves a step by its key.
     *
     * @param key the step key
     * @return the step if declared, otherwise empty
     */
    public Optional<WorkflowStep> stepByKey(String key) {
        return steps.stream().filter(s -> s.key().equals(key)).findFirst();
    }

    /**
     * Resolves the step that follows the given step in the process graph.
     *
     * @param step a step in this definition
     * @return the next step, or empty if {@code step} is terminal
     */
    public Optional<WorkflowStep> nextStep(WorkflowStep step) {
        if (step.nextStepKey() == null) return Optional.empty();
        return stepByKey(step.nextStepKey());
    }

    /**
     * Returns a copy marked inactive — no new instances may be started, but running instances are
     * unaffected. {@code updatedAt} is refreshed.
     */
    public WorkflowDefinition deactivate() {
        return new WorkflowDefinition(id, tenantId, workflowKey, name, version, steps, false,
                createdAt, Instant.now());
    }
}
