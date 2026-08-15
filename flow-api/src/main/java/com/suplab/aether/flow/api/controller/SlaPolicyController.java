package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.BusinessHours;
import com.suplab.aether.flow.domain.SlaPolicy;
import com.suplab.aether.flow.ports.SlaPolicyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-tenant SLA governance policy: the approval SLA budget and the escalation chain.
 *
 * <p>Scoped by {@code tenantId}. {@code GET} returns the stored policy or the defaults a tenant would
 * get with none set; {@code PUT} replaces it. The escalation chain is an ordered list of roles a
 * breached task is routed through by the escalation sweep. An optional {@code businessHours} calendar
 * makes SLA budgets count working time only; omitting it keeps 24/7 wall-clock deadlines.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/sla-policy")
public class SlaPolicyController {

    private static final Logger log = LoggerFactory.getLogger(SlaPolicyController.class);

    private final SlaPolicyStore slaPolicyStore;

    public SlaPolicyController(SlaPolicyStore slaPolicyStore) {
        this.slaPolicyStore = slaPolicyStore;
    }

    /** Request body for replacing a tenant's policy. */
    public record PolicyRequest(Integer defaultSlaMinutes, List<String> escalationChain,
                                BusinessHoursRequest businessHours) {

        /** Convenience for a 24/7 policy request (no business-hours calendar). */
        public PolicyRequest(Integer defaultSlaMinutes, List<String> escalationChain) {
            this(defaultSlaMinutes, escalationChain, null);
        }
    }

    /**
     * Optional business-hours calendar in a policy request. {@code null} (or an absent block) means
     * 24/7 SLAs. {@code zone} is an IANA zone id, {@code start}/{@code end} are {@code HH:mm} times,
     * and {@code days} are {@link DayOfWeek} names (e.g. {@code "MONDAY"}).
     */
    public record BusinessHoursRequest(String zone, String start, String end, List<String> days) {}

    /**
     * Returns the tenant's SLA policy (or the defaults it would use if none is stored).
     *
     * @return 200 OK with the policy view
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@PathVariable String tenantId) {
        var policy = slaPolicyStore.find(tenantId).orElseGet(() -> SlaPolicy.defaultFor(tenantId));
        return ResponseEntity.ok(toView(policy));
    }

    /**
     * Replaces the tenant's SLA policy.
     *
     * @return 200 OK with the stored policy view; 400 on invalid input
     */
    @PutMapping
    public ResponseEntity<Object> put(@PathVariable String tenantId, @RequestBody PolicyRequest request) {
        if (request == null || request.defaultSlaMinutes() == null || request.defaultSlaMinutes() < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "defaultSlaMinutes is required and must be >= 0"));
        }
        try {
            var chain = request.escalationChain() != null ? request.escalationChain() : List.<String>of();
            var policy = new SlaPolicy(tenantId, request.defaultSlaMinutes(), chain,
                    toBusinessHours(request.businessHours()));
            slaPolicyStore.save(policy);
            log.info("Updated SLA policy tenantId={} defaultSlaMinutes={} chain={} businessHours={}",
                    tenantId, policy.defaultSlaMinutes(), policy.escalationChain(), policy.hasBusinessHours());
            return ResponseEntity.ok(toView(policy));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Parses the optional calendar block, or returns {@code null} for a 24/7 policy. */
    private static BusinessHours toBusinessHours(BusinessHoursRequest req) {
        if (req == null) return null;
        if (req.zone() == null || req.start() == null || req.end() == null
                || req.days() == null || req.days().isEmpty()) {
            throw new IllegalArgumentException(
                    "businessHours requires zone, start, end, and at least one day");
        }
        Set<DayOfWeek> days = req.days().stream()
                .map(d -> DayOfWeek.valueOf(d.trim().toUpperCase()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
        return new BusinessHours(ZoneId.of(req.zone().trim()), LocalTime.parse(req.start().trim()),
                LocalTime.parse(req.end().trim()), days);
    }

    private static Map<String, Object> toView(SlaPolicy policy) {
        var view = new LinkedHashMap<String, Object>();
        view.put("tenantId", policy.tenantId());
        view.put("defaultSlaMinutes", policy.defaultSlaMinutes());
        view.put("escalationChain", policy.escalationChain());
        var hours = policy.businessHours();
        if (hours != null) {
            var hoursView = new LinkedHashMap<String, Object>();
            hoursView.put("zone", hours.zone().getId());
            hoursView.put("start", hours.start().toString());
            hoursView.put("end", hours.end().toString());
            hoursView.put("days", hours.workingDays().stream().map(DayOfWeek::name).toList());
            view.put("businessHours", hoursView);
        }
        return view;
    }
}
