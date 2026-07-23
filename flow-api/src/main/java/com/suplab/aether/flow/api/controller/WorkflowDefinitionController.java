package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.StepType;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowStep;
import com.suplab.aether.flow.ports.WorkflowDefinitionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Workflow definition management for a tenant.
 *
 * <p>Every path is scoped by {@code tenantId} — the multi-tenancy boundary of Aether Flow. A
 * definition is registered with its ordered step graph; the domain validates the graph (exactly
 * one END step, unique keys, resolvable transitions) on construction, so malformed processes are
 * rejected with 400 before they can run.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/workflows")
public class WorkflowDefinitionController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionController.class);

    private final WorkflowDefinitionStore definitionStore;

    public WorkflowDefinitionController(WorkflowDefinitionStore definitionStore) {
        this.definitionStore = definitionStore;
    }

    /** Request body for registering a workflow definition. */
    public record CreateWorkflowRequest(String workflowKey, String name, List<StepRequest> steps) {}

    /** One step in a create request. */
    public record StepRequest(String key, String name, String type, int slaMinutes,
                              String assignedRole, String nextStepKey) {}

    /**
     * Registers a workflow definition. The first registration for a {@code workflowKey} is version 1;
     * each subsequent registration <strong>publishes a new version</strong> ({@code prior + 1}) and
     * retires the previously active one. In-flight instances are unaffected — each resolves against
     * its own pinned version.
     *
     * @return 201 Created with the (possibly new-version) definition summary; 400 if the step graph
     *         is invalid
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@PathVariable String tenantId,
                                                      @RequestBody CreateWorkflowRequest request) {
        if (request.workflowKey() == null || request.workflowKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "workflowKey is required"));
        }
        if (request.steps() == null || request.steps().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "steps are required"));
        }
        try {
            var steps = request.steps().stream().map(WorkflowDefinitionController::toStep).toList();
            var scope = FlowScope.of(tenantId, request.workflowKey());
            var existing = definitionStore.findActive(scope);
            // Build (and validate) the new definition BEFORE retiring the old one, so an invalid new
            // graph leaves the currently active version untouched.
            var definition = existing
                    .map(prior -> prior.supersede(request.name(), steps))
                    .orElseGet(() -> WorkflowDefinition.create(scope, request.name(), steps));
            existing.ifPresent(prior -> definitionStore.save(prior.deactivate()));
            definitionStore.save(definition);
            log.info("Published workflow definition tenantId={} workflowKey={} version={} steps={}",
                    tenantId, request.workflowKey(), definition.version(), steps.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(toView(definition));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lists workflow definitions for the tenant, most recently updated first.
     *
     * @return 200 OK with the definition summaries
     */
    @GetMapping
    public ResponseEntity<Object> list(@PathVariable String tenantId,
                                       @RequestParam(defaultValue = "20") int limit) {
        var body = definitionStore.findByTenant(tenantId, limit).stream()
                .map(WorkflowDefinitionController::toView).toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Returns the active definition for a workflow key.
     *
     * @return 200 OK with the definition; 404 if none is active
     */
    @GetMapping("/{workflowKey}")
    public ResponseEntity<Object> getActive(@PathVariable String tenantId,
                                            @PathVariable String workflowKey) {
        return definitionStore.findActive(FlowScope.of(tenantId, workflowKey))
                .<ResponseEntity<Object>>map(def -> ResponseEntity.ok(toView(def)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "no active definition")));
    }

    /**
     * Deletes all versions of a workflow definition within the tenant.
     *
     * @return 204 No Content
     */
    @DeleteMapping("/{workflowKey}")
    public ResponseEntity<Void> delete(@PathVariable String tenantId, @PathVariable String workflowKey) {
        definitionStore.delete(FlowScope.of(tenantId, workflowKey));
        log.info("Deleted workflow definition tenantId={} workflowKey={}", tenantId, workflowKey);
        return ResponseEntity.noContent().build();
    }

    private static WorkflowStep toStep(StepRequest s) {
        StepType type;
        try {
            type = StepType.valueOf(s.type() == null ? "" : s.type().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown step type: " + s.type());
        }
        return new WorkflowStep(s.key(), s.name(), type, s.slaMinutes(), s.assignedRole(), s.nextStepKey());
    }

    private static Map<String, Object> toView(WorkflowDefinition definition) {
        var steps = definition.steps().stream().map(s -> Map.<String, Object>of(
                "key", s.key(),
                "name", s.name(),
                "type", s.type().name(),
                "slaMinutes", s.slaMinutes(),
                "assignedRole", s.assignedRole() != null ? s.assignedRole() : "",
                "nextStepKey", s.nextStepKey() != null ? s.nextStepKey() : "")).toList();
        return Map.of(
                "id", definition.id().toString(),
                "workflowKey", definition.workflowKey(),
                "name", definition.name(),
                "version", definition.version(),
                "active", definition.active(),
                "steps", steps,
                "updatedAt", definition.updatedAt().toString());
    }
}
