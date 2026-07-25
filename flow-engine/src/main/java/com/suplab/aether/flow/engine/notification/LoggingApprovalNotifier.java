package com.suplab.aether.flow.engine.notification;

import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.ports.ApprovalNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link ApprovalNotificationPort} — emits structured SLF4J events for reviewer notifications.
 *
 * <p>This is the dependency-free default so Flow runs standalone with no notification transport
 * configured: raise and escalation signals land in the log stream, where any log-based alerting can
 * pick them up. Webhook and email sinks are adapters behind the same port; the core never depends on
 * a transport. Emitting is best-effort and side-effect-free beyond logging.</p>
 */
public class LoggingApprovalNotifier implements ApprovalNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingApprovalNotifier.class);

    @Override
    public void notifyRaised(ApprovalTask task) {
        log.info("NOTIFY raised tenantId={} taskId={} role={} instanceId={} slaDueAt={}",
                task.tenantId(), task.id(), task.assignedRole(), task.instanceId(), task.slaDueAt());
    }

    @Override
    public void notifyEscalated(ApprovalTask task) {
        log.warn("NOTIFY escalated tenantId={} taskId={} role={} level={} slaDueAt={}",
                task.tenantId(), task.id(), task.assignedRole(), task.escalationLevel(), task.slaDueAt());
    }
}
