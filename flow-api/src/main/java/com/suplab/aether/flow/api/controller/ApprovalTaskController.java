package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.ports.ApprovalTaskStore;
import com.suplab.aether.flow.ports.WorkflowEnginePort;
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
 * The human review queue: listing open approval tasks and recording decisions.
 *
 * <p>Every path is scoped by {@code tenantId}. Deciding a task resumes its parked workflow
 * instance through the engine — an approval advances the instance past the gate, a rejection stops
 * it. Escalation (raising a breached task's visibility) is handled by the scheduled sweep, not by
 * this controller.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/approvals")
public class ApprovalTaskController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalTaskController.class);

    private final ApprovalTaskStore approvalTaskStore;
    private final WorkflowEnginePort engine;

    public ApprovalTaskController(ApprovalTaskStore approvalTaskStore, WorkflowEnginePort engine) {
        this.approvalTaskStore = approvalTaskStore;
        this.engine = engine;
    }

    /** Request body for a decision. */
    public record DecisionRequest(String decidedBy, String comment) {}

    /**
     * Lists open (PENDING or ESCALATED) tasks for a role, oldest waiting first.
     *
     * @return 200 OK with the task views
     */
    @GetMapping
    public ResponseEntity<Object> queue(@PathVariable String tenantId,
                                        @RequestParam(defaultValue = "reviewer") String role,
                                        @RequestParam(defaultValue = "20") int limit) {
        var body = approvalTaskStore.findOpenByRole(tenantId, role, limit).stream()
                .map(ApprovalTaskController::toView).toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Returns a single task by ID.
     *
     * @return 200 OK with the task view; 404 if not found for this tenant
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<Object> get(@PathVariable String tenantId, @PathVariable UUID taskId) {
        return approvalTaskStore.findById(tenantId, taskId)
                .<ResponseEntity<Object>>map(t -> ResponseEntity.ok(toView(t)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "approval task not found")));
    }

    /**
     * Approves the task and advances its workflow instance.
     *
     * @return 200 OK with the resulting instance view; 404 if the task is unknown; 409 if already decided
     */
    @PostMapping("/{taskId}/approve")
    public ResponseEntity<Object> approve(@PathVariable String tenantId, @PathVariable UUID taskId,
                                          @RequestBody DecisionRequest request) {
        return decide(tenantId, taskId, request, true);
    }

    /**
     * Rejects the task and stops its workflow instance.
     *
     * @return 200 OK with the resulting instance view; 404 if the task is unknown; 409 if already decided
     */
    @PostMapping("/{taskId}/reject")
    public ResponseEntity<Object> reject(@PathVariable String tenantId, @PathVariable UUID taskId,
                                         @RequestBody DecisionRequest request) {
        return decide(tenantId, taskId, request, false);
    }

    private ResponseEntity<Object> decide(String tenantId, UUID taskId, DecisionRequest request,
                                          boolean approve) {
        if (request == null || request.decidedBy() == null || request.decidedBy().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "decidedBy is required"));
        }
        try {
            var instance = approve
                    ? engine.approve(tenantId, taskId, request.decidedBy(), request.comment())
                    : engine.reject(tenantId, taskId, request.decidedBy(), request.comment());
            log.info("Decision approve={} taskId={} tenantId={} by={} -> instanceStatus={}",
                    approve, taskId, tenantId, request.decidedBy(), instance.status());
            return ResponseEntity.ok(WorkflowInstanceController.toView(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    private static Map<String, Object> toView(ApprovalTask task) {
        var view = new java.util.HashMap<String, Object>();
        view.put("taskId", task.id().toString());
        view.put("instanceId", task.instanceId().toString());
        view.put("workflowKey", task.workflowKey());
        view.put("stepKey", task.stepKey());
        view.put("assignedRole", task.assignedRole());
        view.put("outcome", task.outcome().name());
        view.put("slaDueAt", task.slaDueAt().toString());
        view.put("createdAt", task.createdAt().toString());
        view.put("decidedBy", task.decidedBy());
        view.put("comment", task.comment());
        return view;
    }
}
