package com.suplab.aether.flow.engine.store;

import com.suplab.aether.flow.domain.BusinessHours;
import com.suplab.aether.flow.domain.SlaPolicy;
import com.suplab.aether.flow.ports.SlaPolicyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JDBC implementation of {@link SlaPolicyStore} backed by the {@code tenant_sla_policy} table.
 *
 * <p>One row per tenant; {@link #save} upserts on {@code tenant_id}. The escalation chain is stored
 * as an ordered, comma-separated list of roles ({@code ''} = empty chain). Explicit column lists and
 * named parameters throughout.</p>
 */
public class JdbcSlaPolicyStore implements SlaPolicyStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcSlaPolicyStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSlaPolicyStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SlaPolicy> find(String tenantId) {
        var sql = """
                SELECT tenant_id, default_sla_minutes, escalation_chain,
                       business_zone, business_start, business_end, business_days
                FROM tenant_sla_policy
                WHERE tenant_id = :tenantId
                """;
        var params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public void save(SlaPolicy policy) {
        var sql = """
                INSERT INTO tenant_sla_policy (tenant_id, default_sla_minutes, escalation_chain,
                    business_zone, business_start, business_end, business_days, updated_at)
                VALUES (:tenantId, :defaultSlaMinutes, :escalationChain,
                    :businessZone, :businessStart, :businessEnd, :businessDays, NOW())
                ON CONFLICT (tenant_id) DO UPDATE SET
                    default_sla_minutes = EXCLUDED.default_sla_minutes,
                    escalation_chain = EXCLUDED.escalation_chain,
                    business_zone = EXCLUDED.business_zone,
                    business_start = EXCLUDED.business_start,
                    business_end = EXCLUDED.business_end,
                    business_days = EXCLUDED.business_days,
                    updated_at = NOW()
                """;
        var hours = policy.businessHours();
        var params = new MapSqlParameterSource()
                .addValue("tenantId", policy.tenantId())
                .addValue("defaultSlaMinutes", policy.defaultSlaMinutes())
                .addValue("escalationChain", String.join(",", policy.escalationChain()))
                .addValue("businessZone", hours != null ? hours.zone().getId() : null)
                .addValue("businessStart", hours != null ? java.sql.Time.valueOf(hours.start()) : null)
                .addValue("businessEnd", hours != null ? java.sql.Time.valueOf(hours.end()) : null)
                .addValue("businessDays", hours != null ? serializeDays(hours.workingDays()) : null);
        jdbc.update(sql, params);
        log.info("Saved SLA policy tenantId={} defaultSlaMinutes={} chainLength={} businessHours={}",
                policy.tenantId(), policy.defaultSlaMinutes(), policy.escalationChain().size(),
                hours != null);
    }

    private SlaPolicy mapRow(ResultSet rs, int row) throws SQLException {
        var chain = rs.getString("escalation_chain");
        List<String> roles = (chain == null || chain.isBlank()) ? List.of() : List.of(chain.split(","));
        var base = new SlaPolicy(rs.getString("tenant_id"), rs.getInt("default_sla_minutes"), roles);
        return base.withBusinessHours(mapBusinessHours(rs));
    }

    /** Reconstructs a {@link BusinessHours} calendar, or {@code null} when the tenant has none. */
    private static BusinessHours mapBusinessHours(ResultSet rs) throws SQLException {
        var zoneId = rs.getString("business_zone");
        var start = rs.getTime("business_start");
        var end = rs.getTime("business_end");
        var days = rs.getString("business_days");
        if (zoneId == null || start == null || end == null || days == null || days.isBlank()) {
            return null;
        }
        return new BusinessHours(ZoneId.of(zoneId), start.toLocalTime(), end.toLocalTime(),
                deserializeDays(days));
    }

    private static String serializeDays(Set<DayOfWeek> days) {
        return days.stream().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    private static Set<DayOfWeek> deserializeDays(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }
}
