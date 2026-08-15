package com.suplab.aether.flow.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.flow.engine.escalation.SlaEscalationService;
import com.suplab.aether.flow.engine.gateway.DefaultApprovalGateway;
import com.suplab.aether.flow.engine.notification.CompositeApprovalNotifier;
import com.suplab.aether.flow.engine.notification.EmailApprovalNotifier;
import com.suplab.aether.flow.engine.notification.LoggingApprovalNotifier;
import com.suplab.aether.flow.engine.notification.WebhookApprovalNotifier;
import com.suplab.aether.flow.engine.orchestration.DefaultWorkflowOrchestrationService;
import com.suplab.aether.flow.engine.store.JdbcApprovalTaskStore;
import com.suplab.aether.flow.engine.store.JdbcSlaPolicyStore;
import com.suplab.aether.flow.engine.store.JdbcWorkflowDefinitionStore;
import com.suplab.aether.flow.engine.store.JdbcWorkflowInstanceStore;
import com.suplab.aether.flow.api.metrics.MicrometerApprovalMetrics;
import com.suplab.aether.flow.ports.ApprovalGatewayPort;
import com.suplab.aether.flow.ports.ApprovalMetricsPort;
import com.suplab.aether.flow.ports.ApprovalNotificationPort;
import com.suplab.aether.flow.ports.ApprovalTaskStore;
import com.suplab.aether.flow.ports.SlaEscalationPort;
import com.suplab.aether.flow.ports.SlaPolicyStore;
import com.suplab.aether.flow.ports.WorkflowDefinitionStore;
import com.suplab.aether.flow.ports.WorkflowEnginePort;
import com.suplab.aether.flow.ports.WorkflowInstanceStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.MailSender;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring configuration for Aether Flow API beans.
 *
 * <p>Wires the JDBC stores, the workflow orchestration engine, the Grid DEFER gateway, the SLA
 * policy store, the approval notifier, and the SLA escalation service using constructor injection.
 * All beans are declared here — never via field {@code @Autowired}. The engine adapters are
 * framework-free; only this config knows how to assemble them from the autoconfigured
 * {@link NamedParameterJdbcTemplate} and {@link ObjectMapper}.</p>
 */
@Configuration
public class FlowApiConfig {

    /**
     * Creates the workflow-definition store (JSONB step graph, scoped by tenant + workflow key).
     */
    @Bean
    public WorkflowDefinitionStore workflowDefinitionStore(NamedParameterJdbcTemplate jdbc,
                                                           ObjectMapper objectMapper) {
        return new JdbcWorkflowDefinitionStore(jdbc, objectMapper);
    }

    /**
     * Creates the workflow-instance store — the durable state that survives restarts.
     */
    @Bean
    public WorkflowInstanceStore workflowInstanceStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcWorkflowInstanceStore(jdbc);
    }

    /**
     * Creates the approval-task store — the human review queue.
     */
    @Bean
    public ApprovalTaskStore approvalTaskStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcApprovalTaskStore(jdbc);
    }

    /**
     * Creates the per-tenant SLA policy store (budget + escalation chain).
     */
    @Bean
    public SlaPolicyStore slaPolicyStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcSlaPolicyStore(jdbc);
    }

    /**
     * Creates the approval notifier. The logging sink is always on so Flow runs standalone; when
     * {@code aether.flow.notification.webhook.url} is set, a best-effort {@link WebhookApprovalNotifier}
     * is fanned in alongside it, and when {@code aether.flow.notification.email.to} is set (and a
     * {@link MailSender} is configured via {@code spring.mail.*}), a best-effort
     * {@link EmailApprovalNotifier} is fanned in too — all via a {@link CompositeApprovalNotifier}. Each
     * is an adapter behind {@link ApprovalNotificationPort}; the engine never talks to a transport
     * directly.
     *
     * @param webhookUrl     optional HTTP sink for raise/escalation signals (blank → no webhook)
     * @param timeoutSeconds per-request connect/read timeout for the webhook (default 10)
     * @param emailTo        optional recipient for raise/escalation emails (blank → no email)
     * @param emailFrom      sender address for notification emails
     * @param mailSender     autoconfigured mail sender (absent → email sink skipped even if a recipient is set)
     */
    @Bean
    public ApprovalNotificationPort approvalNotificationPort(
            @Value("${aether.flow.notification.webhook.url:}") String webhookUrl,
            @Value("${aether.flow.notification.webhook.timeout-seconds:10}") long timeoutSeconds,
            @Value("${aether.flow.notification.email.to:}") String emailTo,
            @Value("${aether.flow.notification.email.from:aether-flow@localhost}") String emailFrom,
            ObjectProvider<MailSender> mailSender) {
        List<ApprovalNotificationPort> sinks = new ArrayList<>();
        sinks.add(new LoggingApprovalNotifier());
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
            requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
            var restClient = RestClient.builder().requestFactory(requestFactory).build();
            sinks.add(new WebhookApprovalNotifier(webhookUrl, restClient));
        }
        if (emailTo != null && !emailTo.isBlank()) {
            var sender = mailSender.getIfAvailable();
            if (sender != null) {
                sinks.add(new EmailApprovalNotifier(sender, emailFrom, emailTo));
            }
        }
        return new CompositeApprovalNotifier(sinks);
    }

    /**
     * Creates the operator metrics adapter — Micrometer counters over the approval lifecycle
     * (raised / approved / rejected / reassigned). Escalation + open-queue metrics live in the
     * escalation scheduler.
     */
    @Bean
    public ApprovalMetricsPort approvalMetricsPort(MeterRegistry meterRegistry) {
        return new MicrometerApprovalMetrics(meterRegistry);
    }

    /**
     * Creates the workflow orchestration engine (the process state machine).
     */
    @Bean
    public WorkflowEnginePort workflowEnginePort(WorkflowDefinitionStore definitionStore,
                                                 WorkflowInstanceStore instanceStore,
                                                 ApprovalTaskStore approvalTaskStore,
                                                 ApprovalNotificationPort notifier,
                                                 ApprovalMetricsPort metrics) {
        return new DefaultWorkflowOrchestrationService(definitionStore, instanceStore, approvalTaskStore,
                notifier, metrics);
    }

    /**
     * Creates the Grid DEFER gateway — turns deferred decisions into human-approval workflows.
     *
     * @param deferralSlaMinutes SLA budget for a deferral's approval task (default 60)
     */
    @Bean
    public ApprovalGatewayPort approvalGatewayPort(
            WorkflowDefinitionStore definitionStore,
            WorkflowInstanceStore instanceStore,
            ApprovalTaskStore approvalTaskStore,
            ApprovalNotificationPort notifier,
            ApprovalMetricsPort metrics,
            @Value("${aether.flow.deferral.sla-minutes:60}") int deferralSlaMinutes) {
        return new DefaultApprovalGateway(definitionStore, instanceStore, approvalTaskStore, notifier,
                metrics, deferralSlaMinutes);
    }

    /**
     * Creates the policy-driven SLA escalation sweep service — routes breached tasks up each tenant's
     * escalation chain and fires escalation notifications.
     *
     * @param batchLimit maximum tasks processed per sweep (default 500)
     */
    @Bean
    public SlaEscalationPort slaEscalationPort(ApprovalTaskStore approvalTaskStore,
                                               SlaPolicyStore slaPolicyStore,
                                               ApprovalNotificationPort notifier,
                                               @Value("${aether.flow.escalation.batch-limit:500}") int batchLimit) {
        return new SlaEscalationService(approvalTaskStore, slaPolicyStore, notifier, batchLimit);
    }
}
