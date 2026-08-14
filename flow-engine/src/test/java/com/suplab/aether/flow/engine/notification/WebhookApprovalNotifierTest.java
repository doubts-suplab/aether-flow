package com.suplab.aether.flow.engine.notification;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.ApprovalTask;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookApprovalNotifierTest {

    private static ApprovalTask task() {
        var now = Instant.now();
        return new ApprovalTask(UUID.randomUUID(), "tenant-1", UUID.randomUUID(), "wf-1",
                "approve-step", "reviewer", ApprovalOutcome.PENDING, now.plusSeconds(3600), now,
                null, null, null, 0);
    }

    @Test
    void blankUrl_isRejected() {
        assertThatThrownBy(() -> new WebhookApprovalNotifier("  ", RestClient.create()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("webhookUrl");
        assertThatThrownBy(() -> new WebhookApprovalNotifier(null, RestClient.create()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("webhookUrl");
    }

    @Test
    void unreachableSink_onRaise_isSwallowedNotThrown() {
        // Best-effort: a dead endpoint must never break task raising.
        var notifier = new WebhookApprovalNotifier("http://127.0.0.1:1/hook", RestClient.create());
        assertThatCode(() -> notifier.notifyRaised(task())).doesNotThrowAnyException();
    }

    @Test
    void unreachableSink_onEscalate_isSwallowedNotThrown() {
        var notifier = new WebhookApprovalNotifier("http://127.0.0.1:1/hook", RestClient.create());
        assertThatCode(() -> notifier.notifyEscalated(task())).doesNotThrowAnyException();
    }
}
