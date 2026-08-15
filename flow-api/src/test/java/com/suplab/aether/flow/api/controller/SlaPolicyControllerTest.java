package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.SlaPolicy;
import com.suplab.aether.flow.ports.SlaPolicyStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SlaPolicyControllerTest {

    private static final class FakePolicyStore implements SlaPolicyStore {
        final Map<String, SlaPolicy> byTenant = new HashMap<>();
        @Override public Optional<SlaPolicy> find(String tenantId) {
            return Optional.ofNullable(byTenant.get(tenantId));
        }
        @Override public void save(SlaPolicy policy) { byTenant.put(policy.tenantId(), policy); }
    }

    @Test
    void get_returnsDefaultsWhenNoneStored() {
        var res = new SlaPolicyController(new FakePolicyStore()).get("tenant-1");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("defaultSlaMinutes")).isEqualTo(SlaPolicy.DEFAULT_SLA_MINUTES);
        assertThat(res.getBody().get("escalationChain")).isEqualTo(List.of());
    }

    @Test
    void put_storesPolicyAndReturnsView() {
        var store = new FakePolicyStore();
        var controller = new SlaPolicyController(store);
        var req = new SlaPolicyController.PolicyRequest(30, List.of("lead", "manager"));

        var res = controller.put("tenant-1", req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(store.byTenant.get("tenant-1").escalationChain()).containsExactly("lead", "manager");
        assertThat(store.byTenant.get("tenant-1").defaultSlaMinutes()).isEqualTo(30);
    }

    @Test
    void put_rejectsMissingOrNegativeBudget() {
        var controller = new SlaPolicyController(new FakePolicyStore());
        assertThat(controller.put("t", new SlaPolicyController.PolicyRequest(null, List.of()))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.put("t", new SlaPolicyController.PolicyRequest(-5, List.of()))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void put_storesBusinessHours_andGetReturnsIt() {
        var store = new FakePolicyStore();
        var controller = new SlaPolicyController(store);
        var hours = new SlaPolicyController.BusinessHoursRequest(
                "Europe/London", "09:00", "17:00", List.of("MONDAY", "FRIDAY"));
        var req = new SlaPolicyController.PolicyRequest(30, List.of("lead"), hours);

        var putRes = controller.put("tenant-1", req);
        assertThat(putRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(store.byTenant.get("tenant-1").hasBusinessHours()).isTrue();

        var getRes = new SlaPolicyController(store).get("tenant-1");
        var view = (Map<String, Object>) getRes.getBody().get("businessHours");
        assertThat(view).isNotNull();
        assertThat(view.get("zone")).isEqualTo("Europe/London");
        assertThat(view.get("start")).isEqualTo("09:00");
        assertThat((List<String>) view.get("days")).containsExactlyInAnyOrder("MONDAY", "FRIDAY");
    }

    @Test
    void put_rejectsIncompleteBusinessHours() {
        var controller = new SlaPolicyController(new FakePolicyStore());
        // Missing zone → 400, not a 500.
        var bad = new SlaPolicyController.BusinessHoursRequest(null, "09:00", "17:00", List.of("MONDAY"));
        assertThat(controller.put("t", new SlaPolicyController.PolicyRequest(30, List.of(), bad))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Unparseable zone → 400 (DateTimeException mapped).
        var badZone = new SlaPolicyController.BusinessHoursRequest("Nowhere/Void", "09:00", "17:00",
                List.of("MONDAY"));
        assertThat(controller.put("t", new SlaPolicyController.PolicyRequest(30, List.of(), badZone))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
