package com.suplab.aether.flow.engine.escalation;

import com.suplab.aether.flow.ports.SlaEscalationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Set-based JDBC implementation of {@link SlaEscalationPort}.
 *
 * <p>The sweep runs as a single {@code UPDATE}: every {@code PENDING} approval task whose
 * {@code sla_due_at} is in the past is transitioned to {@code ESCALATED}. No per-row round trips,
 * so a sweep over a large queue stays cheap. Escalation only raises visibility — the task stays
 * open and a human must still decide it; the sweep never approves, rejects, or deletes.</p>
 */
public class SlaEscalationService implements SlaEscalationPort {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);

    private final NamedParameterJdbcTemplate jdbc;

    public SlaEscalationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EscalationResult sweep() {
        long scanned = countOpen();
        long escalated = escalateBreached();
        long remainingOpen = countOpen();
        log.info("SLA escalation sweep complete: scanned={} escalated={} totalOpen={}",
                scanned, escalated, remainingOpen);
        return new EscalationResult(scanned, escalated, remainingOpen);
    }

    private long escalateBreached() {
        var sql = """
                UPDATE approval_tasks
                SET outcome = 'ESCALATED'
                WHERE outcome = 'PENDING'
                  AND sla_due_at < NOW()
                """;
        return jdbc.update(sql, new MapSqlParameterSource());
    }

    private long countOpen() {
        var sql = "SELECT COUNT(*) FROM approval_tasks WHERE outcome IN ('PENDING', 'ESCALATED')";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return count != null ? count : 0L;
    }
}
