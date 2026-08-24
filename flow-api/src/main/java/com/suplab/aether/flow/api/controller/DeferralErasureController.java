package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.ports.ApprovalErasurePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Right-to-erasure seam for Grid deferrals (GDPR Art. 17).
 *
 * <p>Erases a specific deferred decision — its {@code grid-deferral} workflow instance and every
 * approval task raised to review it — keyed by {@code correlationId} within a tenant. Erasure is
 * precise (per correlation, not tenant-wide) and idempotent: erasing a correlation with nothing to
 * remove returns 404 so a caller can distinguish "erased" from "nothing there", while re-erasing an
 * already-erased correlation is harmless.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/deferrals")
public class DeferralErasureController {

    private static final Logger log = LoggerFactory.getLogger(DeferralErasureController.class);

    private final ApprovalErasurePort erasure;

    public DeferralErasureController(ApprovalErasurePort erasure) {
        this.erasure = erasure;
    }

    /**
     * Erases a deferral and its approval history.
     *
     * @return 200 OK with the counts removed; 404 when the correlation matched nothing to erase
     */
    @DeleteMapping("/{correlationId}")
    public ResponseEntity<Map<String, Object>> erase(@PathVariable String tenantId,
                                                     @PathVariable String correlationId) {
        var result = erasure.eraseDeferral(tenantId, correlationId);
        if (result.isEmpty()) {
            log.info("No deferral to erase correlationId={} tenantId={}", correlationId, tenantId);
            return ResponseEntity.notFound().build();
        }
        log.info("Erased deferral correlationId={} tenantId={} instances={} tasks={}",
                correlationId, tenantId, result.instancesErased(), result.tasksErased());
        return ResponseEntity.ok(Map.of(
                "correlationId", result.correlationId(),
                "tenantId", result.tenantId(),
                "instancesErased", result.instancesErased(),
                "tasksErased", result.tasksErased()));
    }
}
