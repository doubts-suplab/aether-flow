package com.suplab.aether.flow.engine.gateway;

import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStep;
import com.suplab.aether.flow.ports.AgentStepInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP {@link AgentStepInvoker} — POSTs a bounded JSON envelope to a configured Aether Grid agent
 * endpoint when a workflow reaches an {@code AGENT} step.
 *
 * <p>The adapter is wired only when {@code aether.flow.grid.agent-url} is set, so Flow still runs
 * standalone on the {@link AgentStepInvoker#NO_OP} default. The payload carries only routing
 * context — tenant, workflow key, business key, step key/name — never request internals or PII,
 * mirroring the DEFER seam's projection discipline.</p>
 *
 * <p>Invocation is <strong>best-effort</strong>: any transport failure (timeout, 4xx/5xx, unreachable
 * host) is logged and swallowed. An agent step is augmentation, not a gate — a failed agent call must
 * never stop the instance advancing to its next step.</p>
 */
public class HttpGridAgentInvoker implements AgentStepInvoker {

    private static final Logger log = LoggerFactory.getLogger(HttpGridAgentInvoker.class);

    private final String agentUrl;
    private final RestClient restClient;

    public HttpGridAgentInvoker(String agentUrl, RestClient restClient) {
        if (agentUrl == null || agentUrl.isBlank())
            throw new IllegalArgumentException("agentUrl required");
        this.agentUrl = agentUrl.trim();
        this.restClient = restClient;
    }

    @Override
    public void invoke(WorkflowInstance instance, WorkflowStep step) {
        var payload = payload(instance, step);
        try {
            restClient.post().uri(agentUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Invoked Grid agent step instanceId={} tenantId={} stepKey={}",
                    instance.id(), instance.tenantId(), step.key());
        } catch (RuntimeException e) {
            // Best-effort: an agent step is augmentation, not a gate — a failed call never blocks advance.
            log.warn("Grid agent invocation failed instanceId={} stepKey={} url={} — skipping: {}",
                    instance.id(), step.key(), agentUrl, e.getMessage());
        }
    }

    private static Map<String, Object> payload(WorkflowInstance instance, WorkflowStep step) {
        var body = new LinkedHashMap<String, Object>();
        body.put("instanceId", instance.id().toString());
        body.put("tenantId", instance.tenantId());
        body.put("workflowKey", instance.workflowKey());
        body.put("businessKey", instance.businessKey());
        body.put("stepKey", step.key());
        body.put("stepName", step.name());
        return body;
    }
}
