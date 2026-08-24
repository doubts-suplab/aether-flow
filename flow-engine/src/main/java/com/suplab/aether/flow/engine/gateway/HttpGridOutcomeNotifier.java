package com.suplab.aether.flow.engine.gateway;

import com.suplab.aether.flow.domain.DeferralOutcome;
import com.suplab.aether.flow.ports.GridOutcomePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP {@link GridOutcomePort} — POSTs a bounded JSON envelope of a deferral's terminal outcome back to
 * Aether Grid, closing the DEFER seam.
 *
 * <p>The adapter is wired only when {@code aether.flow.grid.callback-url} is set, so Flow still runs
 * standalone on the logging default. The payload carries only the correlation id, tenant, terminal
 * decision, decider, and timestamp — never a comment, request internals, or any PII — mirroring the
 * inbound {@code DeferredDecision} projection's discipline.</p>
 *
 * <p>Delivery is <strong>best-effort</strong>: any transport failure (timeout, 4xx/5xx, unreachable
 * host) is logged and swallowed. The human decision is already durably recorded in Flow — a failed
 * callback must never break the approve/reject path.</p>
 */
public class HttpGridOutcomeNotifier implements GridOutcomePort {

    private static final Logger log = LoggerFactory.getLogger(HttpGridOutcomeNotifier.class);

    private final String callbackUrl;
    private final RestClient restClient;

    public HttpGridOutcomeNotifier(String callbackUrl, RestClient restClient) {
        if (callbackUrl == null || callbackUrl.isBlank())
            throw new IllegalArgumentException("callbackUrl required");
        this.callbackUrl = callbackUrl.trim();
        this.restClient = restClient;
    }

    @Override
    public void reportOutcome(DeferralOutcome outcome) {
        var payload = payload(outcome);
        try {
            restClient.post().uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Reported Grid deferral outcome correlationId={} tenantId={} decision={}",
                    outcome.correlationId(), outcome.tenantId(), outcome.decision());
        } catch (RuntimeException e) {
            // Best-effort: the decision is already recorded in Flow; a failed callback never breaks it.
            log.warn("Grid outcome callback failed correlationId={} url={} — skipping: {}",
                    outcome.correlationId(), callbackUrl, e.getMessage());
        }
    }

    private static Map<String, Object> payload(DeferralOutcome outcome) {
        var body = new LinkedHashMap<String, Object>();
        body.put("correlationId", outcome.correlationId());
        body.put("tenantId", outcome.tenantId());
        body.put("decision", outcome.decision());
        body.put("decidedBy", outcome.decidedBy());
        body.put("decidedAt", outcome.decidedAt().toString());
        return body;
    }
}
