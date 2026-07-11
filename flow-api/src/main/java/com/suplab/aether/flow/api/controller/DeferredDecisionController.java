package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.DeferredDecision;
import com.suplab.aether.flow.ports.ApprovalGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The Aether Grid integration seam — intake for DEFER decisions.
 *
 * <p>Grid's confidence gate defers low-confidence agent decisions to a human. Grid POSTs a bounded
 * {@link DeferredDecision} here; the gateway turns it into a workflow instance parked at a
 * human-approval gate, returning the correlation the reviewer's outcome will be reported under.
 * This endpoint is deliberately <em>not</em> tenant-path-scoped: the tenant travels inside the
 * decision projection, exactly as Grid sends it.</p>
 */
@RestController
@RequestMapping("/api/v1/deferrals")
public class DeferredDecisionController {

    private static final Logger log = LoggerFactory.getLogger(DeferredDecisionController.class);

    private final ApprovalGatewayPort gateway;

    public DeferredDecisionController(ApprovalGatewayPort gateway) {
        this.gateway = gateway;
    }

    /** Request body mirroring Grid's bounded decision projection. */
    public record DeferralRequest(String correlationId, String tenantId, String agentId,
                                  String summary, double confidence, String requestedRole) {}

    /**
     * Accepts a deferred decision from Grid and opens a human-approval workflow for it.
     *
     * @return 201 Created with {@code correlationId}, {@code instanceId}, {@code taskStatus}; 400 on
     *         an invalid decision projection
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> accept(@RequestBody DeferralRequest request) {
        try {
            var decision = new DeferredDecision(request.correlationId(), request.tenantId(),
                    request.agentId(), request.summary(), request.confidence(), request.requestedRole(), null);
            var instance = gateway.accept(decision);
            log.info("Accepted deferral correlationId={} tenantId={} -> instanceId={} status={}",
                    decision.correlationId(), decision.tenantId(), instance.id(), instance.status());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "correlationId", decision.correlationId(),
                    "instanceId", instance.id().toString(),
                    "workflowKey", instance.workflowKey(),
                    "status", instance.status().name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
