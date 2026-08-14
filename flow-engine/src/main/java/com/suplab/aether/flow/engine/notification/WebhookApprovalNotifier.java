package com.suplab.aether.flow.engine.notification;

import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.ports.ApprovalNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Webhook {@link ApprovalNotificationPort} — POSTs a bounded JSON envelope to a configured HTTP sink
 * when an approval task is raised or escalated.
 *
 * <p>The adapter is wired only when {@code aether.flow.notification.webhook.url} is set, so Flow still
 * runs standalone on the logging default. The payload carries only routing metadata — task id, tenant,
 * role, instance/workflow/step keys, escalation level, SLA deadline, and the event type — never a
 * decision, comment, or any Grid/PII content, keeping the notification bounded like the rest of the
 * ecosystem's cross-boundary projections.</p>
 *
 * <p>Delivery is <strong>best-effort</strong>: any transport failure (timeout, 4xx/5xx, unreachable
 * host) is logged and swallowed. A failing webhook must never break task raising or the escalation
 * sweep — a review still exists in the queue whether or not the signal was delivered.</p>
 */
public class WebhookApprovalNotifier implements ApprovalNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(WebhookApprovalNotifier.class);

    private final String webhookUrl;
    private final RestClient restClient;

    public WebhookApprovalNotifier(String webhookUrl, RestClient restClient) {
        if (webhookUrl == null || webhookUrl.isBlank())
            throw new IllegalArgumentException("webhookUrl required");
        this.webhookUrl = webhookUrl.trim();
        this.restClient = restClient;
    }

    @Override
    public void notifyRaised(ApprovalTask task) {
        post("RAISED", task);
    }

    @Override
    public void notifyEscalated(ApprovalTask task) {
        post("ESCALATED", task);
    }

    private void post(String event, ApprovalTask task) {
        var payload = payload(event, task);
        try {
            restClient.post().uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Webhook notified event={} taskId={} tenantId={}", event, task.id(), task.tenantId());
        } catch (RuntimeException e) {
            // Best-effort: a failing sink never breaks raising or the escalation sweep.
            log.warn("Webhook notification failed event={} taskId={} url={} — skipping: {}",
                    event, task.id(), webhookUrl, e.getMessage());
        }
    }

    private static Map<String, Object> payload(String event, ApprovalTask task) {
        var body = new LinkedHashMap<String, Object>();
        body.put("event", event);
        body.put("taskId", task.id().toString());
        body.put("tenantId", task.tenantId());
        body.put("instanceId", task.instanceId().toString());
        body.put("workflowKey", task.workflowKey());
        body.put("stepKey", task.stepKey());
        body.put("assignedRole", task.assignedRole());
        body.put("outcome", task.outcome().name());
        body.put("escalationLevel", task.escalationLevel());
        body.put("slaDueAt", task.slaDueAt().toString());
        return body;
    }
}
