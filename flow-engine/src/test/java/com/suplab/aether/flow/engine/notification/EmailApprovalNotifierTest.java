package com.suplab.aether.flow.engine.notification;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.ApprovalTask;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailApprovalNotifierTest {

    private static ApprovalTask task() {
        var now = Instant.now();
        return new ApprovalTask(UUID.randomUUID(), "tenant-1", UUID.randomUUID(), "wf-1",
                "approve-step", "reviewer", ApprovalOutcome.PENDING, now.plusSeconds(3600), now,
                null, null, null, 0);
    }

    /** Capturing fake — records the messages a notifier would send. */
    private static final class CapturingMailSender implements MailSender {
        final List<SimpleMailMessage> sent = new ArrayList<>();

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            sent.add(simpleMessage);
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            for (var m : simpleMessages) {
                sent.add(m);
            }
        }
    }

    @Test
    void blankRecipientOrSender_isRejected() {
        var mail = new CapturingMailSender();
        assertThatThrownBy(() -> new EmailApprovalNotifier(mail, "from@x", "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("recipient");
        assertThatThrownBy(() -> new EmailApprovalNotifier(mail, "  ", "to@x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sender");
        assertThatThrownBy(() -> new EmailApprovalNotifier(null, "from@x", "to@x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mailSender");
    }

    @Test
    void notifyRaised_sendsBoundedMessage() {
        var mail = new CapturingMailSender();
        var notifier = new EmailApprovalNotifier(mail, "flow@aether", "ops@team");
        var task = task();

        notifier.notifyRaised(task);

        assertThat(mail.sent).hasSize(1);
        var msg = mail.sent.get(0);
        assertThat(msg.getFrom()).isEqualTo("flow@aether");
        assertThat(msg.getTo()).containsExactly("ops@team");
        assertThat(msg.getSubject()).contains("RAISED").contains("tenant-1").contains("reviewer");
        // Body carries only bounded routing metadata — id + keys, never a decision or content.
        assertThat(msg.getText()).contains(task.id().toString()).contains("wf-1").contains("approve-step");
    }

    @Test
    void notifyEscalated_sendsEscalatedEvent() {
        var mail = new CapturingMailSender();
        var notifier = new EmailApprovalNotifier(mail, "flow@aether", "ops@team");

        notifier.notifyEscalated(task());

        assertThat(mail.sent).hasSize(1);
        assertThat(mail.sent.get(0).getSubject()).contains("ESCALATED");
    }

    @Test
    void sendFailure_isSwallowedNotThrown() {
        // Best-effort: an SMTP failure must never break task raising or the escalation sweep.
        MailSender failing = new MailSender() {
            @Override
            public void send(SimpleMailMessage simpleMessage) {
                throw new MailSendException("smtp down");
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) {
                throw new MailSendException("smtp down");
            }
        };
        var notifier = new EmailApprovalNotifier(failing, "flow@aether", "ops@team");
        assertThatCode(() -> notifier.notifyRaised(task())).doesNotThrowAnyException();
        assertThatCode(() -> notifier.notifyEscalated(task())).doesNotThrowAnyException();
    }
}
