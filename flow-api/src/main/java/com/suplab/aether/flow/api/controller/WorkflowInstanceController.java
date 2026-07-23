package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStatus;
import com.suplab.aether.flow.ports.WorkflowEnginePort;
import com.suplab.aether.flow.ports.WorkflowInstanceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Workflow instance lifecycle for a tenant's workflow.
 *
 * <p>Starting an instance drives it through the engine to its first stable state — parked at a
 * human-approval gate or completed. Every path is scoped by {@code tenantId} + {@code workflowKey}.
 * The instance's persisted state is what a restarted service resumes from.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/workflows/{workflowKey}/instances")
public class WorkflowInstanceController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowInstanceController.class);

    private final WorkflowEnginePort engine;
    private final WorkflowInstanceStore instanceStore;

    public WorkflowInstanceController(WorkflowEnginePort engine, WorkflowInstanceStore instanceStore) {
        this.engine = engine;
        this.instanceStore = instanceStore;
    }

    /**
     * Starts a new instance of the active definition and advances it to its first stable state.
     *
     * <p>Request body (optional): {@code {"businessKey": "INV-1001"}}.</p>
     *
     * @return 201 Created with the instance view; 409 if no active definition exists
     */
    @PostMapping
    public ResponseEntity<Object> start(@PathVariable String tenantId,
                                        @PathVariable String workflowKey,
                                        @RequestBody(required = false) Map<String, String> body) {
        var businessKey = body != null ? body.get("businessKey") : null;
        try {
            var instance = engine.start(FlowScope.of(tenantId, workflowKey), businessKey);
            log.info("Started instanceId={} tenantId={} workflowKey={} status={}",
                    instance.id(), tenantId, workflowKey, instance.status());
            return ResponseEntity.status(HttpStatus.CREATED).body(toView(instance));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lists instances of the workflow in a given status, most recently updated first.
     *
     * @param status one of RUNNING, WAITING_APPROVAL, COMPLETED, REJECTED, CANCELLED, FAILED
     * @return 200 OK with the instance views; 400 on an unknown status
     */
    @GetMapping
    public ResponseEntity<Object> list(@PathVariable String tenantId,
                                       @PathVariable String workflowKey,
                                       @RequestParam(defaultValue = "WAITING_APPROVAL") String status,
                                       @RequestParam(defaultValue = "20") int limit) {
        WorkflowStatus parsed;
        try {
            parsed = WorkflowStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown status: " + status));
        }
        var body = instanceStore.findByStatus(FlowScope.of(tenantId, workflowKey), parsed, limit).stream()
                .map(WorkflowInstanceController::toView).toList();
        return ResponseEntity.ok(body);
    }

    /** Request body for a cancellation. */
    public record CancelRequest(String cancelledBy, String reason) {}

    /**
     * Cancels a non-terminal instance (an operator action). Withdraws its open approval task, if
     * any, and stops the instance in {@code CANCELLED}.
     *
     * @return 200 OK with the instance view; 400 if {@code cancelledBy} is missing; 404 if the
     *         instance is unknown; 409 if the instance is already terminal
     */
    @PostMapping("/{instanceId}/cancel")
    public ResponseEntity<Object> cancel(@PathVariable String tenantId,
                                         @PathVariable String workflowKey,
                                         @PathVariable UUID instanceId,
                                         @RequestBody(required = false) CancelRequest request) {
        if (request == null || request.cancelledBy() == null || request.cancelledBy().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "cancelledBy is required"));
        }
        try {
            var instance = engine.cancel(tenantId, instanceId, request.cancelledBy(), request.reason());
            log.info("Cancelled instanceId={} tenantId={} workflowKey={} by={}",
                    instanceId, tenantId, workflowKey, request.cancelledBy());
            return ResponseEntity.ok(toView(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Returns a status breakdown for the workflow — instance counts per status.
     *
     * <p>An operator view over the whole scope: how many instances are RUNNING, WAITING_APPROVAL,
     * COMPLETED, CANCELLED, and so on.</p>
     *
     * @return 200 OK with {@code {tenantId, workflowKey, counts:{STATUS: n, ...}}}
     */
    @GetMapping("/stats")
    public ResponseEntity<Object> stats(@PathVariable String tenantId,
                                        @PathVariable String workflowKey) {
        var scope = FlowScope.of(tenantId, workflowKey);
        var counts = new java.util.LinkedHashMap<String, Long>();
        for (var status : WorkflowStatus.values()) {
            counts.put(status.name(), instanceStore.countByStatus(scope, status));
        }
        return ResponseEntity.ok(Map.of(
                "tenantId", tenantId,
                "workflowKey", workflowKey,
                "counts", counts));
    }

    /**
     * Returns a single instance by ID.
     *
     * @return 200 OK with the instance view; 404 if not found in this workflow
     */
    @GetMapping("/{instanceId}")
    public ResponseEntity<Object> get(@PathVariable String tenantId,
                                      @PathVariable String workflowKey,
                                      @PathVariable UUID instanceId) {
        return instanceStore.findById(FlowScope.of(tenantId, workflowKey), instanceId)
                .<ResponseEntity<Object>>map(i -> ResponseEntity.ok(toView(i)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "instance not found")));
    }

    static Map<String, Object> toView(WorkflowInstance instance) {
        var view = new java.util.HashMap<String, Object>();
        view.put("instanceId", instance.id().toString());
        view.put("workflowKey", instance.workflowKey());
        view.put("definitionVersion", instance.definitionVersion());
        view.put("businessKey", instance.businessKey());
        view.put("currentStepKey", instance.currentStepKey());
        view.put("status", instance.status().name());
        view.put("startedAt", instance.startedAt().toString());
        view.put("updatedAt", instance.updatedAt().toString());
        view.put("completedAt", instance.completedAt() != null ? instance.completedAt().toString() : null);
        return view;
    }
}
