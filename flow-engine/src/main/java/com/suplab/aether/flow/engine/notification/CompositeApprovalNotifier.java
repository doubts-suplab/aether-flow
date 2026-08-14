package com.suplab.aether.flow.engine.notification;

import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.ports.ApprovalNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Fan-out {@link ApprovalNotificationPort} — delivers each signal to every configured sink.
 *
 * <p>Lets Flow run more than one notification transport at once (for example the always-on logging
 * sink plus a webhook). Each delegate is invoked <strong>independently and best-effort</strong>: a
 * throwing sink is logged and skipped so it never suppresses the others or breaks task raising / the
 * escalation sweep. With a single delegate this is a transparent pass-through.</p>
 */
public class CompositeApprovalNotifier implements ApprovalNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(CompositeApprovalNotifier.class);

    private final List<ApprovalNotificationPort> delegates;

    public CompositeApprovalNotifier(List<ApprovalNotificationPort> delegates) {
        this.delegates = delegates == null ? List.of() : List.copyOf(delegates);
    }

    @Override
    public void notifyRaised(ApprovalTask task) {
        for (var delegate : delegates) {
            try {
                delegate.notifyRaised(task);
            } catch (RuntimeException e) {
                log.warn("Notification sink {} failed on raise for taskId={} — skipping: {}",
                        delegate.getClass().getSimpleName(), task.id(), e.getMessage());
            }
        }
    }

    @Override
    public void notifyEscalated(ApprovalTask task) {
        for (var delegate : delegates) {
            try {
                delegate.notifyEscalated(task);
            } catch (RuntimeException e) {
                log.warn("Notification sink {} failed on escalation for taskId={} — skipping: {}",
                        delegate.getClass().getSimpleName(), task.id(), e.getMessage());
            }
        }
    }
}
