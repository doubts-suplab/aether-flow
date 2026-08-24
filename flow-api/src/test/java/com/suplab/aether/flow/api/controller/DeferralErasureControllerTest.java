package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.DeferralErasureResult;
import com.suplab.aether.flow.ports.ApprovalErasurePort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class DeferralErasureControllerTest {

    private static final class FakeErasure implements ApprovalErasurePort {
        DeferralErasureResult result;
        String lastTenant;
        String lastCorrelation;
        @Override public DeferralErasureResult eraseDeferral(String tenantId, String correlationId) {
            lastTenant = tenantId;
            lastCorrelation = correlationId;
            return result;
        }
    }

    @Test
    void erase_returns200WithCountsWhenSomethingWasErased() {
        var erasure = new FakeErasure();
        erasure.result = new DeferralErasureResult("corr-1", "acme", 1, 2);
        var res = new DeferralErasureController(erasure).erase("acme", "corr-1");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("instancesErased", 1).containsEntry("tasksErased", 2);
        assertThat(erasure.lastTenant).isEqualTo("acme");
        assertThat(erasure.lastCorrelation).isEqualTo("corr-1");
    }

    @Test
    void erase_returns404WhenNothingMatched() {
        var erasure = new FakeErasure();
        erasure.result = new DeferralErasureResult("corr-x", "acme", 0, 0);
        var res = new DeferralErasureController(erasure).erase("acme", "corr-x");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
