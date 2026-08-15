package com.suplab.aether.flow.engine.notification;

import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.ports.ApprovalNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSender;

/**
 * Email {@link ApprovalNotificationPort} — sends a bounded plain-text message to a configured recipient
 * when an approval task is raised or escalated.
 *
 * <p>The adapter is wired only when {@code aether.flow.notification.email.to} is set (and a
 * {@link MailSender} is configured), so Flow still runs standalone on the logging default. The body
 * carries only routing metadata — task id, tenant, role, instance/workflow/step keys, escalation level,
 * SLA deadline, and the event type — never a decision, comment, or any Grid/PII content, keeping the
 * notification bounded like the rest of the ecosystem's cross-boundary projections.</p>
 *
 * <p>Delivery is <strong>best-effort</strong>: any send failure (SMTP error, unreachable host,
 * misconfiguration) is logged and swallowed. A failing mail sink must never break task raising or the
 * escalation sweep — a review still exists in the queue whether or not the signal was delivered.</p>
 */
public class EmailApprovalNotifier implements ApprovalNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(EmailApprovalNotifier.class);

    private final MailSender mailSender;
    private final String from;
    private final String to;

    public EmailApprovalNotifier(MailSender mailSender, String from, String to) {
        if (mailSender == null)
            throw new IllegalArgumentException("mailSender required");
        if (to == null || to.isBlank())
            throw new IllegalArgumentException("recipient (to) required");
        if (from == null || from.isBlank())
            throw new IllegalArgumentException("sender (from) required");
        this.mailSender = mailSender;
        this.from = from.trim();
        this.to = to.trim();
    }

    @Override
    public void notifyRaised(ApprovalTask task) {
        send("RAISED", task);
    }

    @Override
    public void notifyEscalated(ApprovalTask task) {
        send("ESCALATED", task);
    }

    private void send(String event, ApprovalTask task) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject(event, task));
        message.setText(body(event, task));
        try {
            mailSender.send(message);
            log.debug("Email notified event={} taskId={} tenantId={}", event, task.id(), task.tenantId());
        } catch (MailException e) {
            // Best-effort: a failing sink never breaks raising or the escalation sweep.
            log.warn("Email notification failed event={} taskId={} to={} — skipping: {}",
                    event, task.id(), to, e.getMessage());
        }
    }

    private static String subject(String event, ApprovalTask task) {
        return "[Aether Flow] Approval %s — %s / %s".formatted(event, task.tenantId(), task.assignedRole());
    }

    private static String body(String event, ApprovalTask task) {
        return """
               Approval task %s.

               Event:           %s
               Task id:         %s
               Tenant:          %s
               Instance:        %s
               Workflow:        %s
               Step:            %s
               Assigned role:   %s
               Outcome:         %s
               Escalation level:%d
               SLA due at:      %s
               """.formatted(
                event.toLowerCase(),
                event,
                task.id(),
                task.tenantId(),
                task.instanceId(),
                task.workflowKey(),
                task.stepKey(),
                task.assignedRole(),
                task.outcome().name(),
                task.escalationLevel(),
                task.slaDueAt());
    }
}
