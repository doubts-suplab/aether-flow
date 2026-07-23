package com.suplab.aether.flow.engine.support;

import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStatus;
import com.suplab.aether.flow.ports.ApprovalTaskStore;
import com.suplab.aether.flow.ports.WorkflowDefinitionStore;
import com.suplab.aether.flow.ports.WorkflowInstanceStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory port implementations for engine unit tests — no database required.
 *
 * <p>These mirror the semantics of the JDBC stores (scoping, UPSERT-by-id, open-queue filtering)
 * so the orchestration and gateway services can be exercised as pure state machines. Integration
 * behaviour of the real JDBC stores is covered separately by the {@code *IT} Testcontainers
 * tests.</p>
 */
public final class InMemoryStores {

    private InMemoryStores() {
    }

    /** In-memory {@link WorkflowDefinitionStore}. */
    public static final class Definitions implements WorkflowDefinitionStore {
        private final Map<UUID, WorkflowDefinition> byId = new ConcurrentHashMap<>();

        @Override
        public void save(WorkflowDefinition definition) {
            byId.put(definition.id(), definition);
        }

        @Override
        public Optional<WorkflowDefinition> findActive(FlowScope scope) {
            return byId.values().stream()
                    .filter(d -> d.tenantId().equals(scope.tenantId())
                            && d.workflowKey().equals(scope.workflowKey()) && d.active())
                    .max(Comparator.comparingInt(WorkflowDefinition::version));
        }

        @Override
        public Optional<WorkflowDefinition> findByVersion(FlowScope scope, int version) {
            return byId.values().stream()
                    .filter(d -> d.tenantId().equals(scope.tenantId())
                            && d.workflowKey().equals(scope.workflowKey()) && d.version() == version)
                    .findFirst();
        }

        @Override
        public List<WorkflowDefinition> findByTenant(String tenantId, int limit) {
            return byId.values().stream()
                    .filter(d -> d.tenantId().equals(tenantId))
                    .sorted(Comparator.comparing(WorkflowDefinition::updatedAt).reversed())
                    .limit(limit).toList();
        }

        @Override
        public void delete(FlowScope scope) {
            byId.values().removeIf(d -> d.tenantId().equals(scope.tenantId())
                    && d.workflowKey().equals(scope.workflowKey()));
        }
    }

    /** In-memory {@link WorkflowInstanceStore}. */
    public static final class Instances implements WorkflowInstanceStore {
        private final Map<UUID, WorkflowInstance> byId = new ConcurrentHashMap<>();

        @Override
        public void save(WorkflowInstance instance) {
            byId.put(instance.id(), instance);
        }

        @Override
        public Optional<WorkflowInstance> findById(FlowScope scope, UUID instanceId) {
            return Optional.ofNullable(byId.get(instanceId))
                    .filter(i -> i.tenantId().equals(scope.tenantId())
                            && i.workflowKey().equals(scope.workflowKey()));
        }

        @Override
        public Optional<WorkflowInstance> findByTenantAndId(String tenantId, UUID instanceId) {
            return Optional.ofNullable(byId.get(instanceId)).filter(i -> i.tenantId().equals(tenantId));
        }

        @Override
        public List<WorkflowInstance> findByStatus(FlowScope scope, WorkflowStatus status, int limit) {
            return byId.values().stream()
                    .filter(i -> i.tenantId().equals(scope.tenantId())
                            && i.workflowKey().equals(scope.workflowKey()) && i.status() == status)
                    .sorted(Comparator.comparing(WorkflowInstance::updatedAt).reversed())
                    .limit(limit).toList();
        }

        @Override
        public long countByStatus(FlowScope scope, WorkflowStatus status) {
            return byId.values().stream()
                    .filter(i -> i.tenantId().equals(scope.tenantId())
                            && i.workflowKey().equals(scope.workflowKey()) && i.status() == status)
                    .count();
        }

        public int size() {
            return byId.size();
        }
    }

    /** In-memory {@link ApprovalTaskStore}. */
    public static final class Tasks implements ApprovalTaskStore {
        private final Map<UUID, ApprovalTask> byId = new ConcurrentHashMap<>();

        @Override
        public void save(ApprovalTask task) {
            byId.put(task.id(), task);
        }

        @Override
        public Optional<ApprovalTask> findById(String tenantId, UUID taskId) {
            return Optional.ofNullable(byId.get(taskId)).filter(t -> t.tenantId().equals(tenantId));
        }

        @Override
        public List<ApprovalTask> findOpenByRole(String tenantId, String role, int limit) {
            return byId.values().stream()
                    .filter(t -> t.tenantId().equals(tenantId) && t.assignedRole().equals(role)
                            && t.outcome().isOpen())
                    .sorted(Comparator.comparing(ApprovalTask::createdAt))
                    .limit(limit).toList();
        }

        @Override
        public Optional<ApprovalTask> findOpenByInstance(String tenantId, UUID instanceId) {
            return byId.values().stream()
                    .filter(t -> t.tenantId().equals(tenantId) && t.instanceId().equals(instanceId)
                            && t.outcome().isOpen())
                    .max(Comparator.comparing(ApprovalTask::createdAt));
        }

        public List<ApprovalTask> all() {
            return new ArrayList<>(byId.values());
        }
    }
}
