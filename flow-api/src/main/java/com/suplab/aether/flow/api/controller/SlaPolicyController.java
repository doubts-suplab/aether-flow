package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.SlaPolicy;
import com.suplab.aether.flow.ports.SlaPolicyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Per-tenant SLA governance policy: the approval SLA budget and the escalation chain.
 *
 * <p>Scoped by {@code tenantId}. {@code GET} returns the stored policy or the defaults a tenant would
 * get with none set; {@code PUT} replaces it. The escalation chain is an ordered list of roles a
 * breached task is routed through by the escalation sweep.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/sla-policy")
public class SlaPolicyController {

    private static final Logger log = LoggerFactory.getLogger(SlaPolicyController.class);

    private final SlaPolicyStore slaPolicyStore;

    public SlaPolicyController(SlaPolicyStore slaPolicyStore) {
        this.slaPolicyStore = slaPolicyStore;
    }

    /** Request body for replacing a tenant's policy. */
    public record PolicyRequest(Integer defaultSlaMinutes, List<String> escalationChain) {}

    /**
     * Returns the tenant's SLA policy (or the defaults it would use if none is stored).
     *
     * @return 200 OK with the policy view
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@PathVariable String tenantId) {
        var policy = slaPolicyStore.find(tenantId).orElseGet(() -> SlaPolicy.defaultFor(tenantId));
        return ResponseEntity.ok(toView(policy));
    }

    /**
     * Replaces the tenant's SLA policy.
     *
     * @return 200 OK with the stored policy view; 400 on invalid input
     */
    @PutMapping
    public ResponseEntity<Object> put(@PathVariable String tenantId, @RequestBody PolicyRequest request) {
        if (request == null || request.defaultSlaMinutes() == null || request.defaultSlaMinutes() < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "defaultSlaMinutes is required and must be >= 0"));
        }
        try {
            var chain = request.escalationChain() != null ? request.escalationChain() : List.<String>of();
            var policy = new SlaPolicy(tenantId, request.defaultSlaMinutes(), chain);
            slaPolicyStore.save(policy);
            log.info("Updated SLA policy tenantId={} defaultSlaMinutes={} chain={}",
                    tenantId, policy.defaultSlaMinutes(), policy.escalationChain());
            return ResponseEntity.ok(toView(policy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static Map<String, Object> toView(SlaPolicy policy) {
        return Map.of(
                "tenantId", policy.tenantId(),
                "defaultSlaMinutes", policy.defaultSlaMinutes(),
                "escalationChain", policy.escalationChain());
    }
}
