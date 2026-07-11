package com.suplab.aether.flow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aether Flow — workflow orchestration platform.
 *
 * <p>Runs on port 8085 (Grid proxy=8080, Grid api=8081, Core=8082, Memory=8083, Vault=8084,
 * Flow=8085). Defines BPMN-style multi-step human-AI processes, drives running instances through a
 * persisted state machine, enforces configurable human approval gates with SLAs and escalation,
 * and receives DEFER decisions from Aether Grid into approval queues.</p>
 *
 * <p>{@code scanBasePackages} covers all sub-packages of {@code com.suplab.aether.flow} so beans
 * from {@code flow-engine} (JDBC stores, orchestration engine, DEFER gateway, escalation service)
 * are discovered via the config class in {@code flow-api}.</p>
 */
@SpringBootApplication(scanBasePackages = "com.suplab.aether.flow")
public class AetherFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(AetherFlowApplication.class, args);
    }
}
